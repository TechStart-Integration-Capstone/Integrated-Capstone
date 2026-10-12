package com.bank.transaction.orchestrator.interest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Objects;

@Component
@ConditionalOnProperty(name = "app.interest.enabled", havingValue = "true")
public class InterestEodWorker implements SchedulingConfigurer {
    private static final Logger log = LoggerFactory.getLogger(InterestEodWorker.class);
    private final InterestEodService service;
    private final String cron;
    private final ZoneId zone;
    private final boolean autoBackfillEnabled;

    public InterestEodWorker(InterestEodService service, String cron, String zone) {
        this(service, cron, zone, false);
    }

    @Autowired
    public InterestEodWorker(InterestEodService service, @Value("${app.interest.cron}") String cron,
                             @Value("${app.interest.zone}") String zone,
                             @Value("${app.interest.auto-backfill-enabled:false}") boolean autoBackfillEnabled) {
        this.service = service;
        this.cron = cron;
        this.zone = ZoneId.of(zone);
        this.autoBackfillEnabled = autoBackfillEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            service.ensureSchemas();
            if (autoBackfillEnabled) {
                int count = service.autoBackfillSavingsLeavingYesterday("admin");
                log.info("Interest startup auto-backfill completed ({} days newly backfilled; yesterday left unfiled for demo)", count);
            }
        } catch (RuntimeException ex) {
            log.error("Interest startup schema check / auto-backfill failed", ex);
        }
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        if (Scheduled.CRON_DISABLED.equals(cron)) return;
        CronTrigger trigger = new CronTrigger(cron, zone);
        AtomicReference<LocalDate> businessDate = new AtomicReference<>();
        // Capture the intended date while scheduling, BEFORE queue delays or midnight can change it.
        // Spring schedules the next execution only after this runnable completes.
        registrar.addTriggerTask(() -> nightly(Objects.requireNonNull(businessDate.get())), context -> {
            var next = trigger.nextExecution(context);
            businessDate.set(next == null ? null : next.atZone(zone).toLocalDate());
            return next;
        });
    }

    public void nightly(LocalDate businessDate) {
        try {
            if (autoBackfillEnabled) {
                service.autoBackfillSavingsLeavingYesterday("admin");
                return;
            }
            service.runBusinessDate(businessDate);
        } catch (RuntimeException ex) {
            log.error("Interest EOD failed for {}; use missing-day recovery if no snapshot committed", businessDate, ex);
        }
    }

    @Scheduled(cron = "${app.interest.recovery-cron}", zone = "${app.interest.zone}")
    public void recover() {
        try {
            if (autoBackfillEnabled) {
                service.autoBackfillSavingsLeavingYesterday("admin");
            }
            var result = service.recoverClosedMonths();
            if (!result.blockedPeriods().isEmpty())
                log.warn("Interest periods awaiting admin recovery (period end -> missing dates): {}", result.blockedPeriods());
        } catch (RuntimeException ex) {
            log.error("Interest month-end recovery failed", ex);
        }
    }
}
