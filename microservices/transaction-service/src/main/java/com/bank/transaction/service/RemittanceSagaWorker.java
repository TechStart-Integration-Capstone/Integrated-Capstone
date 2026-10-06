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
 * Implements forward-recovery saga resolution, 30s cancellation window dispatch,
 * and bounded retry / auto-reversal sweeping:
 *   1. Forward Recovery: Scans for REMITTANCE rows that T24 already posted (status Processing + internal_status POSTED,
 *      or the legacy T24_POSTED status) whose local ledger commit never finished, and completes the debit, credit,
 *      and outbox event idempotently.
 *   2. Matured Client Window: Scans for REMITTANCE rows in Reserved status whose 30s client cancellation window
 *      has elapsed (cancel_until <= NOW), and dispatches them to T24 core banking.
 *   3. Bounded Retry & Auto-Reversal: Scans for REMITTANCE rows waiting on T24 (Processing or old PENDING_CORE).
 *      Applies exponential backoff retries up to maxRetries (3). When max retries are exceeded or core banking
 *      returns a deterministic rejection, automatically reverses held funds back to the customer.
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

    @Scheduled(fixedDelay = 5000)
    public void resolvePendingSagas() {
        LocalDateTime settledBefore = LocalDateTime.now().minus(SETTLE);

        // ── 1. Forward Recovery for T24-posted Remittances ──────────────────────────
        List<Remittance> t24Posted = new ArrayList<>(remittanceRepository.findByStatus(LEGACY_T24_POSTED));
        List<Remittance> awaitingT24 = new ArrayList<>();
        for (Remittance r : remittanceRepository.findByStatus(Remittance.STATUS_PROCESSING)) {
            if (isT24Posted(r)) t24Posted.add(r); else awaitingT24.add(r);
        }

        // Also check legacy uppercase "PROCESSING" if present
        for (Remittance r : remittanceRepository.findByStatus("PROCESSING")) {
            if (!t24Posted.contains(r) && !awaitingT24.contains(r)) {
                if (isT24Posted(r)) t24Posted.add(r); else awaitingT24.add(r);
            }
        }

        for (Remittance remittance : t24Posted) {
            if (!settled(remittance, settledBefore)) continue;
            log.info("[saga-worker] Found T24-posted saga ref={} ftRef={}. Executing forward recovery ledger commit...",
                    remittance.getReferenceNo(), remittance.getFtReference());
            commit(remittance);
        }

        // ── 2. Matured Client 30s Cancellation Window Sweeper ───────────────────────
        List<Remittance> matureWindowList = remittanceRepository.findByStatusAndInternalStatusAndCancelUntilBefore(
                Remittance.STATUS_RESERVED,
                Remittance.INTERNAL_CLIENT_CANCEL_WINDOW,
                LocalDateTime.now()
        );
        for (Remittance remittance : matureWindowList) {
            try {
                log.info("[saga-worker] 30s cancellation window matured for ref={}. Executing core banking saga...",
                        remittance.getReferenceNo());

                RemittanceLedgerService.AccountInfo sourceAcc = ledgerService.resolveAccount(String.valueOf(remittance.getSourceAccountId()));
                RemittanceLedgerService.AccountInfo targetAcc = ledgerService.resolveAccount(String.valueOf(remittance.getTargetAccountId()));

                T24Result t24 = t24AdapterClient.executeTransfer(
                        remittance.getReferenceNo(),
                        sourceAcc.number(),
                        targetAcc.number(),
                        remittance.getAmount(),
                        remittance.getCurrency(),
                        "saga-window-matured-" + System.currentTimeMillis()
                );

                if ("POSTED".equalsIgnoreCase(t24.status())) {
                    ledgerService.recordT24Posted(remittance, t24.ftReference());
                    commit(remittance);
                    log.info("[saga-worker] Matured transfer ref={} successfully posted to core banking.", remittance.getReferenceNo());
                } else if ("REJECTED".equalsIgnoreCase(t24.status())) {
                    // Instant Reversal (0 Retries)
                    ledgerService.releaseHoldFunds(
                            remittance,
                            remittance.getSourceAccountId(),
                            remittance.getAmount(),
                            "Core banking T24 rejected transfer: " + t24.reason()
                    );
                    remittance.setInternalStatus(Remittance.INTERNAL_T24_REJECTED);
                    remittanceRepository.save(remittance);
                    log.info("[saga-worker] Matured transfer ref={} rejected by T24 (/-1). Held funds instantly reversed.", remittance.getReferenceNo());
                } else {
                    // Timeout SLA / Core Down: Transition to PROCESSING and schedule Retry 1 with backoff
                    remittance.setStatus(Remittance.STATUS_PROCESSING);
                    remittance.setInternalStatus(Remittance.STEP_AUTHORIZED);
                    remittance.setCurrentService("t24-adapter");
                    remittance.setRetryCount(0);
                    remittance.setMaxRetries(3);
                    remittance.setNextRetryAt(LocalDateTime.now().plusSeconds(15));
                    remittance.setReason("T24 Core Banking processing delay. Status will update via saga worker.");
                    remittanceRepository.save(remittance);
                    log.info("[saga-worker] Matured transfer ref={} timed out. Scheduled retry 1 at {}",
                            remittance.getReferenceNo(), remittance.getNextRetryAt());
                }
            } catch (Exception e) {
                log.error("[saga-worker] Error executing matured saga for ref={}: {}", remittance.getReferenceNo(), e.getMessage());
            }
        }

        // ── 3. Bounded Retry & Auto-Reversal Sweeper with Exponential Backoff ─────────
        // Sweep legacy PENDING_CORE rows older than 60s into awaitingT24
        LocalDateTime sixtySecondsAgo = LocalDateTime.now().minusSeconds(60);
        for (Remittance r : remittanceRepository.findByStatus("PENDING_CORE")) {
            if (r.getUpdatedAt() != null && r.getUpdatedAt().isBefore(sixtySecondsAgo)) {
                if (!awaitingT24.contains(r)) awaitingT24.add(r);
            }
        }

        for (Remittance remittance : awaitingT24) {
            // Check if backoff interval is active
            if (remittance.getNextRetryAt() != null && remittance.getNextRetryAt().isAfter(LocalDateTime.now())) {
                continue;
            }

            int currentRetries = remittance.getRetryCount() != null ? remittance.getRetryCount() : 0;
            int maxRetries = remittance.getMaxRetries() != null ? remittance.getMaxRetries() : 3;

            // Check if maximum retries reached -> AUTOMATIC BANK-SIDE REVERSAL
            if (currentRetries >= maxRetries) {
                log.warn("[saga-worker] Max retries ({}/{}) reached for ref={}. Initiating automatic bank reversal and releasing hold...",
                        currentRetries, maxRetries, remittance.getReferenceNo());

                String reversalReason = "Core banking unavailable: Maximum retry attempts (" + maxRetries + ") exceeded. Transaction automatically reversed.";
                ledgerService.releaseHoldFunds(
                        remittance,
                        remittance.getSourceAccountId(),
                        remittance.getAmount(),
                        reversalReason
                );
                remittance.setInternalStatus(Remittance.INTERNAL_AUTO_REVERSED);
                remittanceRepository.save(remittance);
                log.info("[saga-worker] Auto-reversal completed for ref={}. Held funds released back to account.", remittance.getReferenceNo());
                continue;
            }

            // Attempt retry
            try {
                log.info("[saga-worker] Inquiring T24 status for saga ref={} retry={}/{}...",
                        remittance.getReferenceNo(), currentRetries + 1, maxRetries);

                RemittanceLedgerService.AccountInfo sourceAcc = ledgerService.resolveAccount(String.valueOf(remittance.getSourceAccountId()));
                RemittanceLedgerService.AccountInfo targetAcc = ledgerService.resolveAccount(String.valueOf(remittance.getTargetAccountId()));

                T24Result t24 = t24AdapterClient.executeTransfer(
                        remittance.getReferenceNo(),
                        sourceAcc.number(),
                        targetAcc.number(),
                        remittance.getAmount(),
                        remittance.getCurrency(),
                        "saga-retry-" + (currentRetries + 1) + "-" + System.currentTimeMillis()
                );

                if ("POSTED".equalsIgnoreCase(t24.status())) {
                    ledgerService.recordT24Posted(remittance, t24.ftReference());
                    commit(remittance);
                    log.info("[saga-worker] Inquiry resolved T24_POSTED for ref={}", remittance.getReferenceNo());
                } else if ("REJECTED".equalsIgnoreCase(t24.status())) {
                    // Instant Reversal (0 further retries)
                    ledgerService.releaseHoldFunds(
                            remittance,
                            remittance.getSourceAccountId(),
                            remittance.getAmount(),
                            "Saga worker inquiry resolved REJECTED: " + t24.reason()
                    );
                    remittance.setInternalStatus(Remittance.INTERNAL_T24_REJECTED);
                    remittanceRepository.save(remittance);
                    log.info("[saga-worker] Inquiry resolved REJECTED for ref={}. Released held funds.", remittance.getReferenceNo());
                } else {
                    // Still failing -> increment retry count and apply exponential backoff
                    int newCount = currentRetries + 1;
                    remittance.setRetryCount(newCount);
                    long delaySeconds = (long) (15 * Math.pow(3, newCount - 1));
                    remittance.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));
                    remittance.setReason("T24 Core Banking retry " + newCount + "/" + maxRetries + " pending. Next retry in " + delaySeconds + "s.");
                    remittanceRepository.save(remittance);
                    log.info("[saga-worker] Retry {}/{} failed for ref={}. Next retry scheduled in {}s at {}",
                            newCount, maxRetries, remittance.getReferenceNo(), delaySeconds, remittance.getNextRetryAt());
                }
            } catch (Exception e) {
                int newCount = currentRetries + 1;
                remittance.setRetryCount(newCount);
                long delaySeconds = (long) (15 * Math.pow(3, newCount - 1));
                remittance.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));
                remittance.setReason("Retry exception: " + e.getMessage());
                remittanceRepository.save(remittance);
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
