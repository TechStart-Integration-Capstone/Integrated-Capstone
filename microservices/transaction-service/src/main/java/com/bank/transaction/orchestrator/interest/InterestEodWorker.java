package com.bank.transaction.orchestrator.interest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.interest.enabled", havingValue = "true")
public class InterestEodWorker {
    private static final Logger log = LoggerFactory.getLogger(InterestEodWorker.class);
    private final InterestEodService service;

    public InterestEodWorker(InterestEodService service) { this.service = service; }

    @Scheduled(cron = "${app.interest.cron}", zone = "${app.interest.zone}")
    public void nightly() {
        try {
            service.runToday();
        } catch (RuntimeException ex) {
            log.error("Interest EOD failed; committed daily snapshots remain safe to retry", ex);
        }
    }

    @Scheduled(cron = "${app.interest.recovery-cron}", zone = "${app.interest.zone}")
    public void recover() {
        try {
            service.recoverClosedMonths();
        } catch (RuntimeException ex) {
            log.error("Interest month-end recovery failed", ex);
        }
    }
}
