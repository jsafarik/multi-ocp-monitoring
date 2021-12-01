package com.github.jsafarik.ocp.monitoring.job.scheduler.listeners;

import org.quartz.JobExecutionContext;
import org.quartz.Trigger;

import javax.enterprise.context.ApplicationScoped;

import lombok.extern.jbosslog.JBossLog;

@ApplicationScoped
@JBossLog
public class TriggerListener implements org.quartz.TriggerListener {
    @Override
    public String getName() {
        return "OpenShiftMonitoringTriggerListener";
    }

    @Override
    public void triggerFired(Trigger trigger, JobExecutionContext context) {
    }

    @Override
    public boolean vetoJobExecution(Trigger trigger, JobExecutionContext context) {
        return false;
    }

    @Override
    public void triggerMisfired(Trigger trigger) {
        log.warn("Trigger " + trigger.getJobKey().getName() + " misfired!");
    }

    @Override
    public void triggerComplete(Trigger trigger, JobExecutionContext context, Trigger.CompletedExecutionInstruction triggerInstructionCode) {
    }
}
