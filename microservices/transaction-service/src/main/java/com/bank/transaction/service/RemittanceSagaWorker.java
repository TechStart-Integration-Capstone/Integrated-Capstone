package com.bank.transaction.service;

import com.bank.transaction.client.T24AdapterClient;
import com.bank.transaction.dto.RemittanceRequest;
import com.bank.transaction.dto.T24Result;
import com.bank.transaction.model.Remittance;
import com.bank.transaction.repository.RemittanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * PayPink 2.0 — Remittance Saga Background Worker.
 *
 * Implements forward-recovery saga resolution & stuck-hold sweeping:
 *   1. Scans for REMITTANCE rows that T24 already posted (status Processing + internal_status POSTED, or the
 *      legacy T24_POSTED status) whose local ledger commit never finished, and completes the debit, credit
 *      and outbox event. commitLedgerMutation is idempotent, so a row the orchestrator just posted is skipped.
 *   2. Scans for REMITTANCE rows still waiting on T24 (Processing without a T24 posting, or old PENDING_CORE)
 *      and queries T24 status to either complete forward recovery or release held funds.
 *
 * Rows touched in the last {@link #SETTLE} are left alone: the orchestrator is probably still working on them.
 */
@Component
public class RemittanceSagaWorker {

    private static final Logger log = LoggerFactory.getLogger(RemittanceSagaWorker.class);
    static final Duration SETTLE = Duration.ofSeconds(15);
    private static final String LEGACY_T24_POSTED = "T24_POSTED";

    private final RemittanceRepository remittanceRepository;
    private final RemittanceLedgerService ledgerService;
    private final T24AdapterClient t24AdapterClient;

    public RemittanceSagaWorker(
            RemittanceRepository remittanceRepository,
            RemittanceLedgerService ledgerService,
            T24AdapterClient t24AdapterClient) {

        this.remittanceRepository = remittanceRepository;
        this.ledgerService = ledgerService;
        this.t24AdapterClient = t24AdapterClient;
    }

    @Scheduled(fixedDelay = 10000)
    public void resolvePendingSagas() {
        LocalDateTime settledBefore = LocalDateTime.now().minus(SETTLE);

        // Status values are compared case-insensitively by SQL Server, so "Processing" also matches "PROCESSING".
        List<Remittance> t24Posted = new ArrayList<>(remittanceRepository.findByStatus(LEGACY_T24_POSTED));
        List<Remittance> awaitingT24 = new ArrayList<>();
        for (Remittance r : remittanceRepository.findByStatus(Remittance.STATUS_PROCESSING)) {
            if (isT24Posted(r)) t24Posted.add(r); else awaitingT24.add(r);
        }
        // Sweep PENDING_CORE rows older than 60 seconds (#1)
        LocalDateTime sixtySecondsAgo = LocalDateTime.now().minusSeconds(60);
        for (Remittance r : remittanceRepository.findByStatus("PENDING_CORE")) {
            if (r.getUpdatedAt() != null && r.getUpdatedAt().isBefore(sixtySecondsAgo)) awaitingT24.add(r);
        }

        // ── 1. Forward Recovery for T24-posted Remittances ──────────────────────────
        for (Remittance remittance : t24Posted) {
            if (!settled(remittance, settledBefore)) continue;
            log.info("[saga-worker] Found T24-posted saga ref={} ftRef={}. Executing forward recovery ledger commit...",
                    remittance.getReferenceNo(), remittance.getFtReference());
            commit(remittance);
        }

        // ── 2. Inquiry Resolution for Remittances still waiting on T24 ───────────────
        for (Remittance remittance : awaitingT24) {
            if (!settled(remittance, settledBefore)) continue;
            try {
                log.info("[saga-worker] Inquiring T24 status for saga ref={} status={}...",
                        remittance.getReferenceNo(), remittance.getStatus());

                RemittanceLedgerService.AccountInfo sourceAcc = ledgerService.resolveAccount(String.valueOf(remittance.getSourceAccountId()));
                RemittanceLedgerService.AccountInfo targetAcc = ledgerService.resolveAccount(String.valueOf(remittance.getTargetAccountId()));

                T24Result t24 = t24AdapterClient.executeTransfer(
                        remittance.getReferenceNo(),
                        sourceAcc.number(),
                        targetAcc.number(),
                        remittance.getAmount(),
                        remittance.getCurrency(),
                        "saga-worker-" + System.currentTimeMillis()
                );

                if ("POSTED".equalsIgnoreCase(t24.status())) {
                    ledgerService.recordT24Posted(remittance, t24.ftReference());
                    log.info("[saga-worker] Inquiry resolved T24_POSTED for ref={}", remittance.getReferenceNo());
                    commit(remittance);
                } else if ("REJECTED".equalsIgnoreCase(t24.status())) {
                    ledgerService.releaseHoldFunds(
                            remittance,
                            remittance.getSourceAccountId(),
                            remittance.getAmount(),
                            "Saga worker inquiry resolved REJECTED: " + t24.reason()
                    );
                    log.info("[saga-worker] Inquiry resolved REJECTED for ref={}. Released held funds.", remittance.getReferenceNo());
                }
            } catch (Exception e) {
                log.error("[saga-worker] Error during T24 status inquiry for ref={}: {}",
                        remittance.getReferenceNo(), e.getMessage());
            }
        }
    }

    static boolean isT24Posted(Remittance r) {
        return LEGACY_T24_POSTED.equalsIgnoreCase(r.getStatus())
                || (Remittance.STEP_POSTED.equals(r.getInternalStatus()) && r.getFtReference() != null);
    }

    private static boolean settled(Remittance r, LocalDateTime settledBefore) {
        return r.getUpdatedAt() == null || r.getUpdatedAt().isBefore(settledBefore);
    }

    private void commit(Remittance remittance) {
        try {
            RemittanceRequest request = new RemittanceRequest();
            request.setSourceAccountId(String.valueOf(remittance.getSourceAccountId()));
            request.setTargetAccountId(String.valueOf(remittance.getTargetAccountId()));
            request.setAmount(remittance.getAmount());
            request.setCurrency(remittance.getCurrency());
            request.setTransactionType(remittance.getTransactionType());

            ledgerService.commitLedgerMutation(
                    remittance,
                    request,
                    remittance.getSourceAccountId(),
                    remittance.getTargetAccountId(),
                    remittance.getAmount(),
                    null,
                    remittance.getFtReference()
            );
            log.info("[saga-worker] Forward recovery completed successfully for ref={}", remittance.getReferenceNo());
        } catch (Exception e) {
            log.error("[saga-worker] Failed to complete forward recovery for ref={}: {}",
                    remittance.getReferenceNo(), e.getMessage());
        }
    }
}
