package com.github.jsafarik.ocp.monitoring.job.impl;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.github.jsafarik.ocp.monitoring.cluster.Cluster;
import com.github.jsafarik.ocp.monitoring.cluster.Kubeconfig;
import com.github.jsafarik.ocp.monitoring.job.MonitoringJob;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.NamedContext;
import io.fabric8.kubernetes.client.internal.KubeConfigUtils;
import lombok.extern.jbosslog.JBossLog;

/**
 * This job does not check any aspect of any cluster, but updates the list of kubeconfigs and clusters.
 */
@JBossLog
@DisallowConcurrentExecution
public class UpdateKubeconfigsJob extends MonitoringJob {

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Map<String, List<String>> kubeconfigsWithFilters = getConfiguration().getKubeconfigsWithFilters();
        for (String kubeconfigUrl : kubeconfigsWithFilters.keySet()) {
            Kubeconfig kubeconfig = this.getManager().getKubeconfig(kubeconfigUrl);
            kubeconfig.updateContents();
            List<Cluster> clusters = new ArrayList<>();
            for (NamedContext ctx : getContexts(kubeconfig.getContents(), kubeconfigsWithFilters.get(kubeconfigUrl))) {
                clusters.add(kubeconfig.getCluster(ctx.getName()));
            }
            kubeconfig.retainClusters(clusters);
        }
    }

    /**
     * Transform the kubeconfig content to a list of contexts
     */
    private List<NamedContext> getContexts(String kubeconfigContents, List<String> filters) {
        List<NamedContext> contexts;

        try {
            contexts = KubeConfigUtils.parseConfigFromString(kubeconfigContents).getContexts();
        } catch (IOException e) {
            return new ArrayList<>();
        }

        if (filters != null && filters.size() > 0) {
            Stream<NamedContext> stream = contexts.stream();
            for (String filter : filters) {
                stream = stream.filter(ctx -> ctx.getName().contains(filter));
            }
            contexts = stream.collect(Collectors.toList());
        }

        return contexts;
    }
}
