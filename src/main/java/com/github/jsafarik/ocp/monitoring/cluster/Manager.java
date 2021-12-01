package com.github.jsafarik.ocp.monitoring.cluster;

import com.github.jsafarik.ocp.monitoring.job.impl.AccessibilityJob;
import com.github.jsafarik.ocp.monitoring.job.impl.DeployCheckJob;

import javax.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * The manager is tracking all kubeconfigs provided through the application's configuration
 */
@ApplicationScoped
public class Manager {

    private Map<String, Kubeconfig> kubeconfigs;

    private MeterRegistry registry;

    public Manager(MeterRegistry registry) {
        this.kubeconfigs = Collections.synchronizedMap(new HashMap<>());
        this.registry = registry;
    }

    /**
     * Get a list of all tracked kubeconfig objects. <br/>
     * The returned list is a new list completely detached from the Map tracking the Kubeconfig objects,
     * but the objects inside are not copied. <br/>
     * This means that changes to the list itself will not be propagated to the Manager Map, but changes to the Kubeconfig objects will.
     */
    public synchronized List<Kubeconfig> getKubeconfigs() {
        return new ArrayList<>(kubeconfigs.values());
    }

    /**
     * Get kubeconfig saved with the given URL. <br/>
     * If such kubeconfig is not in the map, new kubeconfig entry will be created.
     */
    public synchronized Kubeconfig getKubeconfig(String url) {
        if (kubeconfigs.containsKey(url)) {
            return kubeconfigs.get(url);
        }

        Kubeconfig kubeconfig = new Kubeconfig(url, registry);
        kubeconfigs.put(url, kubeconfig);
        return kubeconfig;
    }

    /**
     * Get all URLs of clusters that have their WORKING metric and ACCESSIBILITY metric set to 1.
     */
    public synchronized Set<String> getWorkingClusters() {
        Set<String> clusters = new HashSet<>();
        for (Kubeconfig kubeconfig : kubeconfigs.values()) {
            clusters.addAll(
                kubeconfig.getClusters()
                    .stream()
                    .filter(cluster -> cluster.hasMetric(DeployCheckJob.METRIC_WORKING_NAME) &&
                        cluster.hasMetric(AccessibilityJob.METRIC_ACCESSIBILITY_NAME))
                    .filter(
                        cluster -> cluster.getMetric(DeployCheckJob.METRIC_WORKING_NAME) == 1 &&
                            cluster.getMetric(AccessibilityJob.METRIC_ACCESSIBILITY_NAME) == 1
                    ).map(cluster -> cluster.getClient().getOpenshiftUrl().toString())
                    .collect(Collectors.toSet())
            );
        }
        return clusters;
    }
}
