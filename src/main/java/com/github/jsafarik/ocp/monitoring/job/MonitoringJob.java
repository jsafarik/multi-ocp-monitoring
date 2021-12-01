package com.github.jsafarik.ocp.monitoring.job;

import org.quartz.Job;

import com.github.jsafarik.ocp.monitoring.cluster.Manager;
import com.github.jsafarik.ocp.monitoring.config.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Base class for each job periodically executed. <br/>
 * For each execution a new instance is created.
 */
public abstract class MonitoringJob implements Job {

    @Getter
    @Setter
    private Manager manager;

    @Getter
    @Setter
    private Configuration configuration;

    /**
     * Used to supply the execution period.
     */
    public int getPeriodInSeconds() {
        return 60;
    }

    /**
     * Used to supply the initial delay of the first execution.
     */
    public int getDelayInSeconds() {
        return 0;
    }
}
