package com.github.jsafarik.ocp.monitoring.job.scheduler.listeners;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import javax.enterprise.context.ApplicationScoped;

import lombok.extern.jbosslog.JBossLog;

@ApplicationScoped
@JBossLog
public class JobListener implements org.quartz.JobListener {
    @Override
    public String getName() {
        return "OpenShiftMonitoringJobListener";
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        log.info("Job " + context.getJobDetail().getKey().getName() + " to be executed");
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {

    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        log.info("Job " + context.getJobDetail().getKey().getName() + " was executed");
    }
}
