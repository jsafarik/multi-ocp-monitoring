package com.github.jsafarik.ocp.monitoring.job.impl;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.github.jsafarik.ocp.monitoring.cluster.Cluster;
import com.github.jsafarik.ocp.monitoring.cluster.Kubeconfig;
import com.github.jsafarik.ocp.monitoring.job.MonitoringJob;
import com.github.jsafarik.ocp.monitoring.util.http.HttpUtils;
import com.github.jsafarik.ocp.monitoring.util.http.Response;

import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.jbosslog.JBossLog;

/**
 * Check if each cluster is accessible by retrieving the Console URL (when retrieved -> API is working)
 * and send GET request to the console. <br/><br/>
 * Sets the {@link AccessibilityJob#METRIC_ACCESSIBILITY_NAME} metric on each cluster.
 */
@JBossLog
@DisallowConcurrentExecution
public class AccessibilityJob extends MonitoringJob {

    public static final String METRIC_ACCESSIBILITY_NAME = "cluster.accessible";

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        for (Kubeconfig kubeconfig : getManager().getKubeconfigs()) {
            for (Cluster cluster : kubeconfig.getClusters()) {
                String consoleUrl = checkApiAccessibility(cluster);
                boolean consoleAccessibility = false;
                if (consoleUrl != null) {
                    consoleAccessibility = checkConsoleAccessibility(consoleUrl);
                }
                cluster.updateMetric(METRIC_ACCESSIBILITY_NAME, consoleUrl != null && consoleAccessibility);
            }
        }
    }

    /**
     * Check API accessibility by retrieving the console URL
     */
    private String checkApiAccessibility(Cluster cluster) {
        try {
            return cluster.getClient()
                .config()
                .consoles()
                .list()
                .getItems()
                .stream()
                .map(console -> console.getStatus().getConsoleURL())
                .findFirst()
                .orElse(null);
        } catch (KubernetesClientException ex) {
            log.error("Couldn't retrieve the console URL: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Check that GET request to the console URL returns HTTP 200
     */
    private boolean checkConsoleAccessibility(String consoleUrl) {
        Response response = HttpUtils.doRequest("GET", consoleUrl, null, null);

        if (response.getCode() != 200) {
            log.error("Get request for console " + consoleUrl + " returned " + response.getCode());
            return false;
        }

        return true;
    }
}
