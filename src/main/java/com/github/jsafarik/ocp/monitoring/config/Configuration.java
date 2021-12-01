package com.github.jsafarik.ocp.monitoring.config;

import org.eclipse.microprofile.config.ConfigProvider;

import javax.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import lombok.extern.jbosslog.JBossLog;

@ApplicationScoped
@JBossLog
public class Configuration {

    private final Property NAMESPACE = new Property("sample.app.namespace", "infra-test");
    private final Property KUBECONFIGS = new Property("monitoring.kubeconfig");
    private final Property CONTEXT_FILTER = new Property("monitoring.context.filters");

    /**
     * Get single property by the exact name of the property's name. <br/>
     * The priority is: Environment variable, application.properties, default value
     */
    private synchronized String getProperty(Property property) {
        String p = System.getenv(property.getEnvVarName());

        if (p == null || p.isEmpty()) {
            p = ConfigProvider.getConfig().getValue(property.getApplicationPropertyName(), String.class);
        }

        if (p == null || p.isEmpty()) {
            p = property.getDefaultValue();
        }

        if (p.startsWith("\"") && p.endsWith("\"")) {
            p = p.substring(1, p.length() - 1);
        }

        return p;
    }

    /**
     * Get list of property names containing the provided property's name. <br/>
     * The priority is: Environment variable, application.properties
     */
    private synchronized List<String> getPropertyNames(Property property) {
        List<String> propertyNames = System.getenv().keySet()
            .stream()
            .filter(key -> key.contains(property.getEnvVarName()))
            .collect(Collectors.toList());

        if (propertyNames.isEmpty()) {
            propertyNames = StreamSupport.stream(ConfigProvider.getConfig().getPropertyNames().spliterator(), false)
                .filter(p -> p.contains(property.getApplicationPropertyName()))
                .collect(Collectors.toList());
        }

        return propertyNames;
    }

    public synchronized Map<String, List<String>> getKubeconfigsWithFilters() {
        List<String> kubeconfigPropertyNames = getPropertyNames(KUBECONFIGS);
        List<String> filterPropertyNames = getPropertyNames(CONTEXT_FILTER);

        Map<String, List<String>> kubeconfigsWithFilters = new HashMap<>();

        for (String kubeconfigPropertyName : kubeconfigPropertyNames) {
            List<String> filters = new ArrayList<>();

            if (filterPropertyNames.size() == 1) {
                // If there is only 1 filter, apply it for all kubeconfigs
                filters.addAll(
                    Arrays.asList(getProperty(new Property(Property.envVarToApplicationProperty(filterPropertyNames.get(0)))).split(","))
                );
            } else {
                // Otherwise there should be a filter for each kubeconfig provided
                // Find the correct filter property name for the current kubeconfig property name
                String filterPropertyName = filterPropertyNames
                    .stream()
                    .filter(filter ->
                        Property.envVarToApplicationProperty(filter).contains(
                            Property.envVarToApplicationProperty(kubeconfigPropertyName)
                                .replaceFirst(KUBECONFIGS.getApplicationPropertyName(), ""))
                    ).findFirst()
                    .orElseThrow(() -> new IllegalStateException(""));

                filters.addAll(
                    Arrays.asList(getProperty(new Property(Property.envVarToApplicationProperty(filterPropertyName))).split(","))
                );
            }

            kubeconfigsWithFilters.put(getProperty(new Property(Property.envVarToApplicationProperty(kubeconfigPropertyName))), filters);
        }

        log.info(kubeconfigsWithFilters);
        return kubeconfigsWithFilters;
    }

    public String getNamespace() {
        return getProperty(NAMESPACE);
    }
}
