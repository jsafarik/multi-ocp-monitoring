package com.github.jsafarik.ocp.monitoring.job.impl;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.github.jsafarik.ocp.monitoring.cluster.Cluster;
import com.github.jsafarik.ocp.monitoring.cluster.Kubeconfig;
import com.github.jsafarik.ocp.monitoring.job.MonitoringJob;
import com.github.jsafarik.ocp.monitoring.util.Utils;
import com.github.jsafarik.ocp.monitoring.util.http.HttpUtils;
import com.github.jsafarik.ocp.monitoring.util.http.Response;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.Template;
import lombok.extern.jbosslog.JBossLog;
import okhttp3.Headers;

/**
 * Deploy sample application, check all of its resources and delete it. <br/>
 * Sets the {@link DeployCheckJob#METRIC_WORKING_NAME} metric on each cluster.
 */
@JBossLog
@DisallowConcurrentExecution
public class DeployCheckJob extends MonitoringJob {

    private String namespace;

    private final static String DATABASE_NAME = "sample-app-db";
    private final static String DATABASE_LIST_URL = "https://raw.githubusercontent.com/jsafarik/openshift-sample-app/master/openshift/postgres.yaml";

    private final static String SAMPLE_APP_NAME = "sample-app-server";
    private final static String SAMPLE_APP_URL = "https://raw.githubusercontent.com/jsafarik/openshift-sample-app/master/openshift/sampleApp.yaml";

    public static final String METRIC_WORKING_NAME = "cluster.working";

    @Override
    public int getPeriodInSeconds() {
        return 60 * 60;
    }

    @Override
    public int getDelayInSeconds() {
        return 90;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        this.namespace = getConfiguration().getNamespace();
        for (Kubeconfig kubeconfig : getManager().getKubeconfigs()) {
            for (Cluster cluster : kubeconfig.getClusters()) {
                boolean working = false;
                if (deploy(cluster)) {
                    working = check(cluster);
                }
                boolean destroyed = destroy(cluster);
                cluster.updateMetric(METRIC_WORKING_NAME, working && destroyed);
            }
        }
    }

    /**
     * Create all resources necessary for the sample application and verify that the resources are created.
     */
    private boolean deploy(Cluster cluster) {
        return createNewProject(cluster)
            && deployDatabase(cluster)
            && verifyDatabaseDeployed(cluster)
            && deploySampleApp(cluster)
            && verifySampleAppDeployed(cluster);
    }

    /**
     * Check that everything is working properly by using the sample application and inspection of logs
     */
    private boolean check(Cluster cluster) {
        return verifySampleAppWorking(cluster) && verifySampleAppLog(cluster);
    }

    /**
     * Destroy all the created resources by destruction of the project
     */
    private boolean destroy(Cluster cluster) {
        Boolean deleted;
        try {
            deleted = cluster.getClient().projects().withName(namespace).delete();
        } catch (KubernetesClientException ex) {
            log.error("Couldn't delete project associated with sample application: " + ex.getMessage());
            return false;
        }

        // Verify that the return value of the delete operation returned true
        if (deleted == null || !deleted) {
            log.error("Couldn't delete project associated with sample application on cluster " + cluster.getClient().getOpenshiftUrl());
            return false;
        }

        // Wait for the namespace to disappear from the project list
        try {
            Utils.waitFor(() -> cluster.getClient().projects().list().getItems()
                    .stream()
                    .map(p -> p.getMetadata().getName())
                    .noneMatch(p -> p.equals(namespace)),
                60);
        } catch (TimeoutException ex) {
            log.error("Couldn't verify that the project is no longer present on cluster " + cluster.getClient().getOpenshiftUrl());
            return false;
        }

        return true;
    }

    /**
     * Create new project in provided cluster and wait up to 60 seconds for the project to appear in the projects list.<br/>
     *
     * @return true if everything went well and there is a new project created, false in case something was not possible to do
     */
    private boolean createNewProject(Cluster cluster) {
        // Obtain a user used to create the project
        String user;
        try {
            user = cluster.getClient().currentUser().getMetadata().getName();
        } catch (KubernetesClientException ex) {
            log.error("Couldn't retrieve current user name on cluster " + cluster.getClient().getOpenshiftUrl());
            return false;
        }

        // Try to create the project
        try {
            cluster.getClient()
                .projects()
                .createProjectAndRoleBindings(
                    namespace,
                    "OpenShift monitoring test application",
                    namespace.replace("-", " "),
                    user,
                    user);
        } catch (KubernetesClientException ex) {
            log.error("Couldn't create new project on cluster " + cluster.getClient().getOpenshiftUrl());
            return false;
        }

        // Verify that the project was created
        try {
            Utils.waitFor(() -> cluster.getClient().projects().list().getItems()
                    .stream()
                    .map(p -> p.getMetadata().getName())
                    .anyMatch(p -> p.equals(namespace)),
                60);
        } catch (TimeoutException ex) {
            log.error("Couldn't verify that the project has been created on cluster " + cluster.getClient().getOpenshiftUrl());
            return false;
        }

        return true;
    }

    /**
     * Deploy the list containing resources necessary for the database necessary for the sample application. <br/>
     *
     * @return true if everything went well, otherwise false
     */
    private boolean deployDatabase(Cluster cluster) {
        try {
            KubernetesList list = cluster.getClient().lists().inNamespace(namespace).load(new URL(DATABASE_LIST_URL)).get();
            cluster.getClient().lists().inNamespace(namespace).create(list);
        } catch (KubernetesClientException ex) {
            log.error("Couldn't create the database list: " + ex.getMessage(), ex);
            return false;
        } catch (MalformedURLException e) {
            log.error("Database list URL is not a proper URL");
            return false;
        }

        return true;
    }

    /**
     * Allow up to 10 minutes for the verification that all the database resources were created. <br/>
     *
     * @return true if everything went well, otherwise false
     */
    private boolean verifyDatabaseDeployed(Cluster cluster) {
        try {
            Utils.waitFor(() -> {
                boolean service = cluster.getClient().services().inNamespace(namespace).list().getItems()
                    .stream()
                    .anyMatch(svc -> DATABASE_NAME.equals(svc.getMetadata().getName()));

                boolean persistentVolumeClaim = cluster.getClient().persistentVolumeClaims().inNamespace(namespace).list().getItems()
                    .stream()
                    .anyMatch(pvc -> DATABASE_NAME.equals(pvc.getMetadata().getName()));

                boolean deploymentConfig = cluster.getClient().deploymentConfigs().inNamespace(namespace).list().getItems()
                    .stream()
                    .anyMatch(dc -> DATABASE_NAME.equals(dc.getMetadata().getName()));

                boolean pod = cluster.getClient().pods().inNamespace(namespace).list().getItems()
                    .stream()
                    .anyMatch(p -> p.getMetadata().getName().contains(DATABASE_NAME)
                        && !p.getMetadata().getName().contains("deploy")
                        && p.getStatus().getContainerStatuses().stream().anyMatch(ContainerStatus::getReady));

                return service && persistentVolumeClaim && deploymentConfig && pod;
            }, 600);
        } catch (TimeoutException e) {
            log.error("Couldn't verify the database resources in time on cluster " + cluster.getClient().getOpenshiftUrl());
            return false;
        }

        return true;
    }

    /**
     * Create and process the template for the sample application, use the list returned by the template processing to create all the resources. <br/>
     * Needs to find the database's service IP address.
     *
     * @return true if everything went well, otherwise false
     */
    private boolean deploySampleApp(Cluster cluster) {
        // Create map for template parameters
        Map<String, String> params = new HashMap<>();
        params.put("APP_NAME", SAMPLE_APP_NAME);

        // Try to find the database's service IP address
        String databaseServiceIP;
        try {
            Optional<Service> databaseService = cluster.getClient().services().inNamespace(namespace).list().getItems()
                .stream()
                .filter(svc -> svc.getMetadata().getName().contains(DATABASE_NAME))
                .findFirst();
            if (databaseService.isPresent()) {
                databaseServiceIP = databaseService.get().getSpec().getClusterIP();
            } else {
                log.error("Couldn't find the database's service on cluster " + cluster.getClient().getOpenshiftUrl());
                return false;
            }
        } catch (KubernetesClientException ex) {
            log.error("Couldn't retrieve the database's service: " + ex.getMessage());
            return false;
        }
        params.put("DB_SERVICE_IP", databaseServiceIP);

        // Deploy resources using the template
        try {
            Template template = cluster.getClient().templates().inNamespace(namespace).load(new URL(SAMPLE_APP_URL)).get();
            cluster.getClient().templates().inNamespace(namespace).createOrReplace(template);
            KubernetesList list = cluster.getClient().templates().inNamespace(namespace).withName(template.getMetadata().getName()).process(params);
            cluster.getClient().lists().inNamespace(namespace).create(list);
        } catch (KubernetesClientException ex) {
            log.error("Couldn't deploy from template: " + ex.getMessage());
            return false;
        } catch (MalformedURLException e) {
            log.error("Sample application template URL is not a proper URL");
            return false;
        }

        return true;
    }

    /**
     * Allow up to 10 minutes for the verification that all the sample application resources were created. <br/>
     *
     * @return true if everything went well, otherwise false
     */
    private boolean verifySampleAppDeployed(Cluster cluster) {
        try {
            Utils.waitFor(() -> {
                boolean service = cluster.getClient().services().inNamespace(namespace).list().getItems()
                    .stream()
                    .anyMatch(svc -> SAMPLE_APP_NAME.equals(svc.getMetadata().getName()));

                boolean route = cluster.getClient().routes().inNamespace(namespace).list().getItems()
                    .stream()
                    .anyMatch(r -> SAMPLE_APP_NAME.equals(r.getMetadata().getName()));

                boolean deploymentConfig = cluster.getClient().deploymentConfigs().inNamespace(namespace).list().getItems()
                    .stream()
                    .anyMatch(dc -> SAMPLE_APP_NAME.equals(dc.getMetadata().getName()));

                boolean pod = cluster.getClient().pods().inNamespace(namespace).list().getItems()
                    .stream()
                    .anyMatch(p -> {
                        String name = p.getMetadata().getName();
                        return name.contains(SAMPLE_APP_NAME)
                            && !name.contains("deploy")
                            && !name.contains("build")
                            && p.getStatus().getContainerStatuses().stream().anyMatch(ContainerStatus::getReady)
                            && cluster.getClient().pods().inNamespace(namespace).withName(name).getLog().contains("Sample app has started!");
                    });

                boolean buildConfig = cluster.getClient().buildConfigs().inNamespace(namespace).list().getItems()
                    .stream()
                    .anyMatch(bc -> SAMPLE_APP_NAME.equals(bc.getMetadata().getName()));

                boolean baseImageStream = cluster.getClient().imageStreams().inNamespace(namespace).list().getItems()
                    .stream()
                    .anyMatch(is -> "openjdk-11".equals(is.getMetadata().getName()));

                boolean sampleAppImageStream = cluster.getClient().imageStreams().inNamespace(namespace).list().getItems()
                    .stream()
                    .anyMatch(is -> SAMPLE_APP_NAME.equals(is.getMetadata().getName()));

                return service && route && deploymentConfig && pod && buildConfig && baseImageStream && sampleAppImageStream;
            }, 600);
        } catch (TimeoutException e) {
            log.error("Didn't verify the sample app resources in time on cluster: " + cluster.getClient().getOpenshiftUrl());
            return false;
        }

        return true;
    }

    /**
     * Find the sample application's route and use it to verify the application is running by using its REST endpoints. <br/>
     *
     * @return true if everything went well, otherwise false
     */
    private boolean verifySampleAppWorking(Cluster cluster) {
        Optional<Route> route;
        try {
            route = cluster.getClient()
                .routes()
                .inNamespace(namespace)
                .withLabelSelector(
                    new LabelSelectorBuilder().withMatchLabels(Collections.singletonMap("app", SAMPLE_APP_NAME)).build())
                .list()
                .getItems()
                .stream()
                .findFirst();
        } catch (KubernetesClientException ex) {
            log.error("Couldn't retrieve sample application route: " + ex.getMessage());
            return false;
        }

        if (route.isEmpty()) {
            log.error("No route associated with the sample application found on cluster " + cluster.getClient().getOpenshiftUrl());
            return false;
        }

        String url = "http://" + route.get().getSpec().getHost();

        Response response = HttpUtils.doRequest("GET", url + "/init", null, null);
        if (!response.getBody().contains("Tasks table created")) {
            log.error("Sample test application didn't confirm database table creation: " + response.getBody() + response.getCode());
            return false;
        }

        response = HttpUtils.doRequest("POST", url + "/add", Headers.of("Content-Type", "text/plain"), "my first task");
        if (!response.getBody().contains("Success")) {
            log.error("Sample test application didn't confirm addition of a new element");
            return false;
        }

        response = HttpUtils.doRequest("GET", url + "/get/1", null, null);
        if (!response.getBody().contains("my first task")) {
            log.error("Sample test application didn't return requested element");
            return false;
        }

        response = HttpUtils.doRequest("GET", url + "/drop", null, null);
        if (!response.getBody().contains("Tasks table dropped")) {
            log.error("Sample test application didn't confirm database table was dropped");
            return false;
        }

        return true;
    }

    /**
     * Verify that the sample application's pod contains all the logs it should after the {@link DeployCheckJob#verifySampleAppWorking} method. <br/>
     *
     * @return true if everything went well, otherwise false
     */
    private boolean verifySampleAppLog(Cluster cluster) {
        try {
            return cluster.getClient().pods().inNamespace(namespace).list().getItems()
                .stream()
                .filter(p -> {
                    String name = p.getMetadata().getName();
                    return name.contains(SAMPLE_APP_NAME)
                        && !name.contains("deploy")
                        && !name.contains("build");
                }).anyMatch(pod -> {
                    String log = cluster.getClient().pods().inNamespace(namespace).withName(pod.getMetadata().getName()).getLog();
                    return log.contains("Initialized DB")
                        && log.contains("Added new task")
                        && log.contains("Got task")
                        && log.contains("Dropped task_list table");
                });
        } catch (KubernetesClientException ex) {
            log.error("Something went wrong while retrieving sample app pod's log: " + ex.getMessage());
            return false;
        }
    }
}
