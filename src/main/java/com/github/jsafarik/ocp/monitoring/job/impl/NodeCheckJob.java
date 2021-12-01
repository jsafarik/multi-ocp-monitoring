package com.github.jsafarik.ocp.monitoring.job.impl;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.github.jsafarik.ocp.monitoring.cluster.Cluster;
import com.github.jsafarik.ocp.monitoring.cluster.Kubeconfig;
import com.github.jsafarik.ocp.monitoring.job.MonitoringJob;

import java.util.List;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.jbosslog.JBossLog;

/**
 * Check each cluster's nodes. Checks how many nodes each cluster have and how many of them are in a "ready" state. <br/><br/>
 * Sets the {@link NodeCheckJob#METRIC_NODE_COUNT_NAME} and {@link NodeCheckJob#METRIC_READY_NODE_COUNT_NAME} metrics on each cluster.
 */
@JBossLog
@DisallowConcurrentExecution
public class NodeCheckJob extends MonitoringJob {

    public static final String METRIC_NODE_COUNT_NAME = "cluster.node.count";
    public static final String METRIC_READY_NODE_COUNT_NAME = "cluster.ready.node.count";

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        for (Kubeconfig kubeconfig : getManager().getKubeconfigs()) {
            for (Cluster cluster : kubeconfig.getClusters()) {
                List<Node> nodes = getAllNodes(cluster);
                if (nodes == null) {
                    continue;
                }
                cluster.updateMetric(METRIC_NODE_COUNT_NAME, nodes.size());

                nodes = getWorkingNodes(nodes);
                cluster.updateMetric(METRIC_READY_NODE_COUNT_NAME, nodes.size());
            }
        }
    }

    /**
     * Fetch all the nodes in a cluster
     */
    private List<Node> getAllNodes(Cluster cluster) {
        try {
            return cluster.getClient()
                .nodes()
                .list()
                .getItems();
        } catch (KubernetesClientException ex) {
            log.error("Couldn't retrieve node list");
            return null;
        }
    }

    /**
     * Filter the fetched nodes with the "ready" state set to true
     */
    private List<Node> getWorkingNodes(List<Node> nodes) {
        return nodes.stream().filter(node ->
            node.getStatus()
                .getConditions()
                .stream()
                .anyMatch(condition -> "ready".equals(condition.getType().toLowerCase()) && Boolean.parseBoolean(condition.getStatus()))
        ).collect(Collectors.toList());
    }
}
