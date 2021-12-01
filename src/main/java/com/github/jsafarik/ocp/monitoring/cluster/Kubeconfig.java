package com.github.jsafarik.ocp.monitoring.cluster;

import com.github.jsafarik.ocp.monitoring.util.http.HttpUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;

/**
 * Class representing each kubeconfig tracked by this monitoring app
 */
public class Kubeconfig {

    @Getter
    private String url;

    @Getter
    private String contents;

    private Map<String, Cluster> clusters;

    private MeterRegistry registry;

    public Kubeconfig(String url, MeterRegistry registry) {
        this.clusters = Collections.synchronizedMap(new HashMap<>());
        this.url = url;
        this.registry = registry;
        updateContents();
    }

    /**
     * Get a list of all tracked cluster objects. <br/>
     * The returned list is a new list completely detached from the Map tracking the Cluster objects,
     * but the objects inside are not copied. <br/>
     * This means that changes to the list itself will not be propagated to the Kubeconfig Map, but changes to the Cluster objects will.
     */
    public synchronized List<Cluster> getClusters() {
        return new ArrayList<>(clusters.values());
    }

    /**
     * Fetch and update the currently saved content of the provided kubeconfig.
     */
    public void updateContents() {
        this.contents = HttpUtils.doRequest("GET", url, null, null).getBody();
    }

    /**
     * Get cluster saved with the given context name. <br/>
     * If such cluster is not in the map, new cluster entry will be created.
     */
    public synchronized Cluster getCluster(String context) {
        if (clusters.containsKey(context)) {
            return clusters.get(context);
        }

        Cluster cluster = new Cluster(contents, context, registry);
        clusters.put(context, cluster);
        return cluster;
    }

    /**
     * Close all clusters not in the provided list and remove them from the internal map. <br/>
     * This will remove all the metrics connected with the removed clusters. <br/>
     */
    public synchronized void retainClusters(List<Cluster> clusters) {
        this.clusters.values().stream().filter(cluster -> !clusters.contains(cluster)).forEach(Cluster::close);
        this.clusters.values().retainAll(clusters);
    }
}
