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

import java.util.List;

/**
 * PayPink 2.0 — Remittance Saga Background Worker.
 *
 * Implements forward-recovery saga resolution:
 *   1. Scans for REMITTANCE rows in T24_POSTED state (T24 committed, local DB pending)
 *      and completes the local ledger debit, credit, and outbox event idempotently.
 *   2. Scans for REMITTANCE rows in PROCESSING state (T24 SLA timeout)
 *      and queries T24 status to either complete forward recovery or release held funds.
 */
@Component
public class RemittanceSagaWorker {

    private static final Logger log = LoggerFactory.getLogger(RemittanceSagaWorker.class);

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
        // ── 1. Forward Recovery for T24_POSTED Remittances ─────────────────────────
        List<Remittance> t24PostedList = remittanceRepository.findByStatus("T24_POSTED");
        for (Remittance remittance : t24PostedList) {
            try {
                log.info("[saga-worker] Found T24_POSTED saga ref={} ftRef={}. Executing forward recovery ledger commit...",
                        remittance.getReferenceNo(), remittance.getFtReference());

                RemittanceRequest request = new RemittanceRequest();
                request.setSourceAccountId(String.valueOf(remittance.getSourceAccountId()));
                request.setTargetAccountId(String.valueOf(remittance.getTargetAccountId()));
                request.setAmount(remittance.getAmount());
                request.setCurrency(remittance.getCurrency());

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

        // ── 2. Inquiry Resolution for PROCESSING Remittances ─────────────────────────
        List<Remittance> processingList = remittanceRepository.findByStatus("PROCESSING");
        for (Remittance remittance : processingList) {
            try {
                log.info("[saga-worker] Inquiring T24 status for PROCESSING saga ref={}...", remittance.getReferenceNo());

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
                } else if ("REJECTED".equalsIgnoreCase(t24.status())) {
                    ledgerService.releaseHoldFunds(
                            remittance,
                            remittance.getSourceAccountId(),
                            remittance.getAmount(),
                            "Core banking T24 rejected transfer: " + t24.reason()
                    );
                    log.info("[saga-worker] Inquiry resolved REJECTED for ref={}. Released held funds.", remittance.getReferenceNo());
                }
            } catch (Exception e) {
                log.error("[saga-worker] Error during T24 status inquiry for ref={}: {}",
                        remittance.getReferenceNo(), e.getMessage());
            }
        }
    }
}
