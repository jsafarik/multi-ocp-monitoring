package com.github.jsafarik.ocp.monitoring.job.scheduler;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

import com.github.jsafarik.ocp.monitoring.job.MonitoringJob;
import com.github.jsafarik.ocp.monitoring.job.factory.MonitoringJobFactory;
import com.github.jsafarik.ocp.monitoring.job.scheduler.listeners.JobListener;
import com.github.jsafarik.ocp.monitoring.job.scheduler.listeners.TriggerListener;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

import io.quarkus.runtime.StartupEvent;
import lombok.extern.jbosslog.JBossLog;

/**
 * Class that creates all JobDetails and Triggers on start of the application. <br/>
 * All classes extending MonitoringJob and stated in the "resources/META-INF/services/com.github.jsafarik.ocp.monitoring.job.MonitoringJob"
 */
@ApplicationScoped
@JBossLog
public class JobScheduler {

    private Scheduler quartzScheduler;

    public JobScheduler(Scheduler scheduler, MonitoringJobFactory factory, JobListener jobListener, TriggerListener triggerListener)
        throws SchedulerException {
        this.quartzScheduler = scheduler;
        this.quartzScheduler.setJobFactory(factory);
        this.quartzScheduler.getListenerManager().addJobListener(jobListener);
        this.quartzScheduler.getListenerManager().addTriggerListener(triggerListener);
    }

    public void registerMonitoringJobs(@Observes StartupEvent event) {
        StreamSupport.stream(ServiceLoader.load(MonitoringJob.class).spliterator(), false).forEach(job -> {
            JobDetail j = JobBuilder.newJob(job.getClass())
                .withIdentity(job.getClass().getSimpleName(), job.getClass().getPackageName())
                .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(job.getClass().getSimpleName(), job.getClass().getPackageName())
                .startAt(Timestamp.valueOf(LocalDateTime.now().plusSeconds(job.getDelayInSeconds())))
                .withSchedule(
                    SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(job.getPeriodInSeconds())
                        .withMisfireHandlingInstructionNextWithRemainingCount()
                        .repeatForever())
                .build();

            try {
                quartzScheduler.scheduleJob(j, trigger);
            } catch (SchedulerException e) {
                throw new IllegalStateException("Couldn't schedule job " + job.getClass().getSimpleName());
            }
        });
    }
}
