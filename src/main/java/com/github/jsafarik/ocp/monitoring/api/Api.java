package com.github.jsafarik.ocp.monitoring.api;

import com.github.jsafarik.ocp.monitoring.cluster.Manager;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import java.util.Set;

import lombok.extern.jbosslog.JBossLog;

/**
 * Minimalistic API which can be used by other services (Jenkins, scripts, ...)
 */
@Path("/clusters")
@JBossLog
public class Api {

    private Manager manager;

    public Api(Manager manager) {
        this.manager = manager;
    }

    /**
     * List all currently working OCP clusters
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Set<String> getWorkingClusters() {
        return manager.getWorkingClusters();
    }

    /**
     * Return if given cluster is working
     */
    @GET
    @Path("{cluster}")
    @Produces(MediaType.APPLICATION_JSON)
    public boolean isClusterRunning(@PathParam("cluster") String cluster) {
        return manager.getWorkingClusters().stream().anyMatch(c -> c.contains(cluster));
    }
}
