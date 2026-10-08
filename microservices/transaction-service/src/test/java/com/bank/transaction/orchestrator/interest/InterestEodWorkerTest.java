package com.bank.transaction.orchestrator.interest;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.SimpleTriggerContext;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InterestEodWorkerTest {
    @Test void scheduledTaskRetainsIntendedDateWhenExecutedAfterMidnight() {
        var service = mock(InterestEodService.class);
        var worker = new InterestEodWorker(service, "0 55 23 * * *", "Asia/Manila");
        var registrar = new ScheduledTaskRegistrar();
        worker.configureTasks(registrar);
        var task = registrar.getTriggerTaskList().get(0);
        var context = new SimpleTriggerContext(Clock.fixed(Instant.parse("2026-09-30T15:54:00Z"), ZoneOffset.UTC));
        assertThat(task.getTrigger().nextExecution(context)).isEqualTo(Instant.parse("2026-09-30T15:55:00Z"));
        // This callback may actually execute after midnight; its date comes from the scheduled instant.
        task.getRunnable().run();
        verify(service).runBusinessDate(LocalDate.of(2026, 9, 30));
        verifyNoMoreInteractions(service);
    }

    @Test void nextExecutionAdvancesPinnedDateAndDisabledCronSchedulesNothing() {
        var service = mock(InterestEodService.class);
        var registrar = new ScheduledTaskRegistrar();
        new InterestEodWorker(service, "-", "Asia/Manila").configureTasks(registrar);
        assertThat(registrar.getTriggerTaskList()).isEmpty();
        new InterestEodWorker(service, "0 55 23 * * *", "Asia/Manila").configureTasks(registrar);
        var task = registrar.getTriggerTaskList().get(0);
        var context = new SimpleTriggerContext(Clock.fixed(Instant.parse("2026-09-30T15:54:00Z"), ZoneOffset.UTC));
        var first = task.getTrigger().nextExecution(context);
        context.update(first, first.plusSeconds(600), first.plusSeconds(601));
        assertThat(task.getTrigger().nextExecution(context)).isEqualTo(Instant.parse("2026-10-01T15:55:00Z"));
        task.getRunnable().run();
        verify(service).runBusinessDate(LocalDate.of(2026, 10, 1));
    }
}
