# Multi-OCP Monitoring

This project is a monitoring solution for multiple OCP clusters. The application is monitoring the cluster accessibility (both API and Console), node count (overall and ready), and its status by deploying a sample application to each monitored cluster.

The application provides the monitored data on the `/q/metrics` endpoint, and it can be consumed by Prometheus.

This project uses Quarkus, the Supersonic Subatomic Java Framework.

## Endpoints

The application is exposing 2 endpoints:

- `/clusters` - lists all currently accessible and working clusters (must pass the accessibility and sample application checks)
- `/clusters/{name}` - lists all currently accessible and working clusters containing the `{name}` path parameter.

## Deployment

### OpenShift

The `openshift` directory contains two files:

- `multiOcpMonitoring.yaml` - deploys the Multi OCP Monitoring application bundled with a Prometheus instance. The deployment automatically points the Prometheus to the monitoring app.
- `grafana.yaml` - deploy a Grafana instance. The datasource for prometheus needs to be added manually to start creating dashboards.

Both templates can be deployed using the command `oc process -f openshift/{file} | oc create -f -` where `{file}` is the name of the template file. To specify parameter value, add `-p KEY=value` with the correct key and value.

## Configuration

Configuration of this application can be done using:
- Environment variables
- application.properties

The priority of the configuration is Environment variables > application.properties > default value.

Some configuration properties might have default values and don't need to be reconfigured.

### Properties

| Environment variable name  | application.properties name | Default value | Description                                                                                                                                                                                                                                                                                                                                                                                                                               |
|----------------------------|-----------------------------|---------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| SAMPLE_APP_NAMESPACE       | sample.app.namespace        | infra-test    | Specify name of project used for sample application deployment check                                                                                                                                                                                                                                                                                                                                                                      |
| MONITORING_KUBECONFIG      | monitoring.kubeconfig       |               | Specify kubeconfig used to create a client for OCP interaction. For multiple kubeconfig files, add unique suffix to the property names                                                                                                                                                                                                                                                                                                    |
| MONITORING_CONTEXT_FILTERS | monitoring.context.filters  |               | Specify filters for the OCP contexts. For example, if you want to use only contexts with name containing the words "admin" and "fo", put "admin,fo" as the property value. If you supplied multiple kubeconfigs with unique suffixes in their property names, the filters must contain the same suffix to correctly link with its kubeconfig. Additionally, when you specify only 1 filter property, it will be used for each kubeconfig. |

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
./mvnw compile quarkus:dev
```

## Packaging and running the application

The application can be packaged using:
```shell script
./mvnw package
```
It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:
```shell script
./mvnw package -Dquarkus.package.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using: 
```shell script
./mvnw package -Pnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using: 
```shell script
./mvnw package -Pnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/monitoring-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/maven-tooling.
