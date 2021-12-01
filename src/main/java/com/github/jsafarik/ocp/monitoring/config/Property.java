package com.github.jsafarik.ocp.monitoring.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Getter
@AllArgsConstructor
@RequiredArgsConstructor
public class Property {

    @NonNull
    private String applicationPropertyName;
    private String defaultValue = "";

    public static String envVarToApplicationProperty(String envvar) {
        return envvar.toLowerCase().replace("_", ".");
    }

    public static String applicationPropertyToEnvVar(String applicationProperty) {
        return applicationProperty.toUpperCase().replace(".", "_");
    }

    public String getEnvVarName() {
        return applicationPropertyToEnvVar(this.applicationPropertyName);
    }
}
