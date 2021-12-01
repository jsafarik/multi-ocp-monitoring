package com.github.jsafarik.ocp.monitoring.job.factory;

import org.quartz.Job;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.simpl.PropertySettingJobFactory;
import org.quartz.spi.TriggerFiredBundle;

import com.github.jsafarik.ocp.monitoring.cluster.Manager;
import com.github.jsafarik.ocp.monitoring.config.Configuration;
import com.github.jsafarik.ocp.monitoring.job.MonitoringJob;

import javax.enterprise.context.ApplicationScoped;

/**
 * Custom Job factory used to provide each job object the Manager instance
 */
@ApplicationScoped
public class MonitoringJobFactory extends PropertySettingJobFactory {

    private Manager manager;
    private Configuration configuration;

    public MonitoringJobFactory(Manager manager, Configuration configuration) {
        this.manager = manager;
        this.configuration = configuration;
    }

    @Override
    public Job newJob(TriggerFiredBundle bundle, Scheduler scheduler) throws SchedulerException {
        Job job = super.newJob(bundle, scheduler);

        if (job instanceof MonitoringJob) {
            ((MonitoringJob) job).setManager(manager);
            ((MonitoringJob) job).setConfiguration(configuration);
        }

        return job;
    }
}
