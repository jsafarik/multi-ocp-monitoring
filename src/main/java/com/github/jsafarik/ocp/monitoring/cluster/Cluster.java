package com.github.jsafarik.ocp.monitoring.cluster;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import lombok.Getter;

/**
 * Class representing each cluster tracked by this monitoring app
 */
public class Cluster {

    Map<String, AtomicInteger> metrics;

    @Getter
    private OpenShiftClient client;

    private boolean deleted;

    private MeterRegistry registry;

    public Cluster(String kubeconfigContents, String context, MeterRegistry registry) {
        Config config = Config.fromKubeconfig(context, kubeconfigContents, null);
        this.client = new DefaultOpenShiftClient(config);

        this.metrics = new HashMap<>();

        this.deleted = false;
        this.registry = registry;
    }

    /**
     * Set the cluster delete flag to true and remove all Meters associated with the cluster. <br/>
     * The delete flag set to true should prevent any update or creation of a new metric.
     */
    public synchronized void close() {
        this.deleted = true;

        List<Meter> meters = registry
            .getMeters()
            .stream()
            .filter(meter -> meter.getId().getTags().contains(Tag.of("API", client.getOpenshiftUrl().toString())))
            .collect(Collectors.toList());

        for (Meter meter : meters) {
            this.registry.remove(meter);
        }
    }

    /**
     * Update or create a new boolean metric. <br/>
     * Each metric has default tag named API containing the clusters URL. <br/>
     * The boolean's true/false will be transformed to 1/0 respectively.
     */
    public synchronized void updateMetric(String name, boolean value, String... additionalTags) {
        updateMetric(name, value ? 1 : 0, additionalTags);
    }

    /**
     * Update or create a new integer metric. <br/>
     * Each metric has default tag named API containing the clusters URL.
     */
    public synchronized void updateMetric(String name, int value, String... additionalTags) {
        if (!deleted) {
            if (!metrics.containsKey(name)) {
                Tags tags = Tags.of("API", client.getOpenshiftUrl().toString()).and(additionalTags);
                metrics.put(name, new AtomicInteger(value));
                registry.gauge(name, tags, metrics.get(name));
            } else {
                metrics.get(name).set(value);
            }
        }
    }

    public synchronized boolean hasMetric(String name) {
        return metrics.containsKey(name);
    }

    public synchronized int getMetric(String name) {
        return metrics.get(name).get();
    }
}
