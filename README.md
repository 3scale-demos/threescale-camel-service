# threescale-camel-service

This project leverages **Red Hat build of Quarkus 2.13.x**, the Supersonic Subatomic Java Framework. More specifically, the project is implemented using [**Red Hat Camel Extensions for Quarkus 2.13.x**](https://access.redhat.com/documentation/en-us/red_hat_integration/2022.q3/html/getting_started_with_camel_extensions_for_quarkus/index).

This camel proxy service can be leveraged to configure the [_Red Hat 3scale APIcast Camel Service policy_](https://access.redhat.com/documentation/en-us/red_hat_3scale_api_management/2.12/html/administering_the_api_gateway/apicast-policies#camel-service_standard-policies). 

The camel proxy service uses the OAuth2 _client credentials flow_ to retrieve an access token from _Red Hat SSO_, and then sets it in the _Authorization_ HTTP header before proxying the request to the upstream backend.

## O. Prerequisites

- Maven 3.8.1+
- JDK 17 installed with `JAVA_HOME` configured appropriately
- A running [_Red Hat SSO_](https://access.redhat.com/documentation/en-us/red_hat_single_sign-on) instance. The following must be configured:
    1. A confidential client with the following characteristics:
        - Client ID: `threescale-camel-service`
        - Client Protocol: `openid-connect`
        - Access type: `confidential`
        - OpenID Connect flow: `service account (client credentials)`
    2. Replace the `client secret` in:
        - `quarkus.oidc-client.credentials.secret` property in the [`application.yml`](./src/main/resources/application.yml) file
        - `quarkus.oidc-client.credentials.secret` property of the `threescale-camel-service-secret` in the [`openshift.yml`](./src/main/kubernetes/openshift.yml) file
    3. Replace the `OIDC authorization server URL` in:
        - `quarkus.oidc-client.auth-server-url`  property in the [`application.yml`](./src/main/resources/application.yml) file
        - `quarkus.oidc-client.auth-server-url` property of the `threescale-camel-service-secret` in the [`openshift.yml`](./src/main/kubernetes/openshift.yml) file
- A running [_Red Hat OpenShift_](https://access.redhat.com/documentation/en-us/openshift_container_platform) cluster
- A running [_Red Hat 3scale API Management_](https://access.redhat.com/documentation/en-us/red_hat_3scale_api_management) platform


## 1. Generate a Java Keystore

```shell script
keytool -genkey -keypass P@ssw0rd -storepass P@ssw0rd -alias threescale-camel-service -keyalg RSA \
-dname "CN=threescale-camel-service" \
-validity 3600 -keystore ./tls-keys/keystore.p12 -v \
-ext san=DNS:threescale-camel-service.svc,DNS:threescale-camel-service.svc.cluster.local,DNS:threescale-camel-service.camel-quarkus.svc,DNS:threescale-camel-service.camel-quarkus.svc.cluster.local,DNS:threescale-camel-service.ceq-services-jvm.svc,DNS:threescale-camel-service.ceq-services-jvm.svc.cluster.local,DNS:threescale-camel-service.ceq-services-native.svc,DNS:threescale-camel-service.ceq-services-native.svc.cluster.local
```

## 2. Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
./mvnw compile quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.

## 3. Packaging and running the application locally

1. Execute the following command line:
    ```shell script
    ./mvnw package
    ```
    It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
    Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

    The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

2. **OPTIONAL:** Creating a native executable

    You can create a native executable using: 
    ```shell script
    ./mvnw package -Pnative
    ```

    Or, if you don't have GraalVM installed, you can run the native executable build in a container using: 
    ```shell script
    ./mvnw package -Pnative -Dquarkus.native.container-build=true
    ```

    You can then execute your native executable with: `./target/threescale-camel-service-1.0.0-SNAPSHOT-runner`

    If you want to learn more about building native executables, please consult https://quarkus.io/guides/maven-tooling.

3. Running Jaeger locally

    [**Jaeger**](https://www.jaegertracing.io/), a distributed tracing system for observability ([_open tracing_](https://opentracing.io/)). :bulb: A simple way of starting a Jaeger tracing server is with `docker` or `podman`:
    1. Start the Jaeger tracing server:
        ```
        podman run --rm -e COLLECTOR_ZIPKIN_HOST_PORT=:9411 -e COLLECTOR_OTLP_ENABLED=true \
        -p 6831:6831/udp -p 6832:6832/udp \
        -p 5778:5778 -p 16686:16686 -p 4317:4317 -p 4318:4318 -p 14250:14250  -p 14268:14268 -p 14269:14269 -p 9411:9411 \
        quay.io/jaegertracing/all-in-one:latest
        ```
    2. While the server is running, browse to http://localhost:16686 to view tracing events.

4. Test locally
    ```shell script
    print "GET http://localhost:8080/q/health HTTP/1.1\nHost: localhost\nAccept: */*\n\n" | ncat --no-shutdown --ssl localhost 9443
    ```

## 4. Packaging and running the application on Red Hat OpenShift

### :bulb: Pre-requisites

- Access to a [Red Hat OpenShift](https://access.redhat.com/documentation/en-us/openshift_container_platform) cluster v4
- User has self-provisioner privilege or has access to a working OpenShift project
- **OPTIONAL**: [**Jaeger**](https://www.jaegertracing.io/), a distributed tracing system for observability ([_open tracing_](https://opentracing.io/)).

### :bulb: Instructions to package using Quarkus JVM mode and deploy to OpenShift

1. Login to the OpenShift cluster
    ```shell script
    oc login ...
    ```
2. Create an OpenShift project or use your existing OpenShift project. For instance, to create `ceq-services-jvm`
    ```shell script
    oc new-project ceq-services-jvm --display-name="Red Hat Camel Extensions for Quarkus Apps - JVM Mode"
    ```
3. Create secret containing the keystore
    ```shell script
    oc create secret generic threescale-camel-service-keystore-secret \
    --from-file=keystore.p12=./tls-keys/keystore.p12
    ```
4. Adjust the `quarkus.opentelemetry.tracer.exporter.otlp.endpoint` property of the `threescale-camel-service-secret` in the [`openshift.yml`](./src/main/kubernetes/openshift.yml) file according to your OpenShift environment and where you installed the [_Jaeger_](https://www.jaegertracing.io/) server.
5. Package and deploy to OpenShift
    ```shell script
    ./mvnw clean package -Dquarkus.kubernetes.deploy=true -Dquarkus.container-image.group=ceq-services-jvm
    ```

### :bulb: ALTERNATIVE - Instructions to package using Quarkus native mode and deploy to OpenShift

> :warning: **Pre-requisites**
- For native compilation, a Linux X86_64 operating system or an OCI (Open Container Initiative) compatible container runtime, such as Podman or Docker is required.

1. Login to the OpenShift cluster
    ```shell script
    oc login ...
    ```
2. Create an OpenShift project or use your existing OpenShift project. For instance, to create `ceq-services-native`
    ```shell script
    oc new-project ceq-services-native --display-name="Red Hat Camel Extensions for Quarkus Apps - Native Mode"
    ```
3. Create secret containing the keystore
    ```shell script
    oc create secret generic threescale-camel-service-keystore-secret \
    --from-file=keystore.p12=./tls-keys/keystore.p12
    ```
4. Adjust the `quarkus.opentelemetry.tracer.exporter.otlp.endpoint` property of the `threescale-camel-service-secret` in the [`openshift.yml`](./src/main/kubernetes/openshift.yml) file according to your OpenShift environment and where you installed the [_Jaeger_](https://www.jaegertracing.io/) server.
5. Package and deploy to OpenShift
    -  Using podman to build the native binary:
        ```shell script
        ./mvnw clean package -Pnative \
        -Dquarkus.kubernetes.deploy=true \
        -Dquarkus.native.container-runtime=podman \
        -Dquarkus.native.builder-image=registry.access.redhat.com/quarkus/mandrel-21-jdk17-rhel8:latest \
        -Dquarkus.container-image.group=ceq-services-native 
        ```
    -  Using docker to build the native binary:
        ```shell script
        ./mvnw clean package -Pnative \
        -Dquarkus.kubernetes.deploy=true \
        -Dquarkus.native.container-runtime=docker \
        -Dquarkus.native.builder-image=registry.access.redhat.com/quarkus/mandrel-21-jdk17-rhel8:latest \
        -Dquarkus.container-image.group=ceq-services-native 
        ```

## 5. How to configure the _APICast Camel Service_ policy to use this service

### :bulb: Pre-requisite

- An API Product configured in _Red Hat 3scale API Management_. For instance, the sample `Echo API` can be used.

### :bulb: Instructions

1. Add and configure the [_APICast Camel Service_](https://access.redhat.com/documentation/en-us/red_hat_3scale_api_management/2.12/html/administering_the_api_gateway/apicast-policies#camel-service_standard-policies) policy on the API Product
    - Reference: https://access.redhat.com/documentation/en-us/red_hat_3scale_api_management/2.12/html/administering_the_api_gateway/transform-with-policy-extension_3scale#configure-apicast-policy-extension-in-fuse_3scale
2. Beware of the following note:
    > **NOTE**: 
    You cannot use `curl` (or any other HTTP client) to test the Camel HTTP proxy directly because the proxy does not support HTTP tunneling using the `CONNECT` method. When using HTTP tunneling with `CONNECT`, the transport is end-to-end encrypted, which does not allow the Camel HTTP proxy to mediate the payload. 
    You may test this with 3scale, which implements this as if proxying via HTTP but establishes a new TLS session towards the Camel application. If you need to perform integration tests against the Camel application you need to use a custom HTTP client. 
    You can use something like: 
    `print "GET https://<backend url> HTTP/1.1\nHost: <backend host>\nAccept: */*\n\n" | ncat --no-shutdown --ssl <camel proxy app host> <camel proxy app port>`

Below is a screenshot of the [_Camel Service_](https://access.redhat.com/documentation/en-us/red_hat_3scale_api_management/2.12/html/administering_the_api_gateway/apicast-policies#camel-service_standard-policies) policy configuration:

![APICast Camel Service Policy](./images/CamelServicePolicy.png)

Below is a sample test where you can notice the `Authorization` HTTP header added and populated with the retrieved OpenID Connect access token (`HTTP_AUTHORIZATION` header in the `Echo API` response):

```shell script
http -v 'https://echo-api.apps.cluster-l5mt5.l5mt5.sandbox1873.opentlc.com:443/demo' user_key:fb61a7d34e82c83b029216a3ca2e24e6
```
```shell script
GET /demo HTTP/1.1
Accept: */*
Accept-Encoding: gzip, deflate
Connection: keep-alive
Host: echo-api.apps.cluster-l5mt5.l5mt5.sandbox1873.opentlc.com:443
User-Agent: HTTPie/3.2.1
user_key: fb61a7d34e82c83b029216a3ca2e24e6

HTTP/1.1 200 OK
cache-control: private
content-type: application/json
date: Wed, 10 Aug 2022 13:03:05 GMT
server: envoy
set-cookie: d0df2ffbd348521e2eef0bdddd2b78c1=83e0250661bd946000044c7d5d01a9a8; path=/; HttpOnly; Secure; SameSite=None
transfer-encoding: chunked
vary: Origin
x-3scale-echo-api: echo-api/1.0.3
x-content-type-options: nosniff
x-envoy-upstream-service-time: 1

{
    "args": "",
    "body": "",
    "headers": {
        "CONTENT_LENGTH": "0",
        "HTTP_ACCEPT": "*/*,*/*",
        "HTTP_ACCEPT_ENCODING": "gzip, deflate,gzip, deflate",
        "HTTP_AUTHORIZATION": "Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJYbHBmQzVaT3dZUVZVdlRHREppRmxMT3lVTXhFZkFLSUdiMDdxcEN6dlBBIn0.eyJleHAiOjE2NjAxMzY3NzcsImlhdCI6MTY2MDEzNjQ3NywianRpIjoiNTUxMWJkNjgtNTk4ZS00YTU2LTk5YTUtYTdjZDk2NjFlMzhlIiwiaXNzIjoiaHR0cHM6Ly9zc28uYXBwcy5jbHVzdGVyLWw1bXQ1Lmw1bXQ1LnNhbmRib3gxODczLm9wZW50bGMuY29tL2F1dGgvcmVhbG1zL29wZW5zaGlmdC1jbHVzdGVyIiwiYXVkIjoiYWNjb3VudCIsInN1YiI6Ijc1MzljOGVkLTgxNWQtNGFiMC04N2FiLTNlYTFjNDYzZTc3MyIsInR5cCI6IkJlYXJlciIsImF6cCI6InRocmVlc2NhbGUtY2FtZWwtc2VydmljZSIsInJlYWxtX2FjY2VzcyI6eyJyb2xlcyI6WyJkZWZhdWx0LXJvbGVzLW9wZW5zaGlmdC1jbHVzdGVyIiwib2ZmbGluZV9hY2Nlc3MiLCJ1bWFfYXV0aG9yaXphdGlvbiJdfSwicmVzb3VyY2VfYWNjZXNzIjp7ImFjY291bnQiOnsicm9sZXMiOlsibWFuYWdlLWFjY291bnQiLCJtYW5hZ2UtYWNjb3VudC1saW5rcyIsInZpZXctcHJvZmlsZSJdfX0sInNjb3BlIjoiZW1haWwgcHJvZmlsZSIsImNsaWVudElkIjoidGhyZWVzY2FsZS1jYW1lbC1zZXJ2aWNlIiwiZW1haWxfdmVyaWZpZWQiOmZhbHNlLCJjbGllbnRIb3N0IjoiMy43NC42Ny4yNDgiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJzZXJ2aWNlLWFjY291bnQtdGhyZWVzY2FsZS1jYW1lbC1zZXJ2aWNlIiwiY2xpZW50QWRkcmVzcyI6IjMuNzQuNjcuMjQ4In0.R2r5ByPw8HwcDAZzWOxpRzFKQu7dhp6aPJkT1j-UAAhVMYsqQRzWhb0nBN1Pd7svyy1pZqI_brmKSkprkCcOH8evgokDqTsTwW8DGtrBNCEEaigSwuRGnctWK2nhifjQBg3hbLxN5PO_VUXmn5bLvk6N0WKvAyFcgM-EMQXwrBEw80MjM6EnOuSAyY0vYyAK2_D_UNgtMy0sCVFH0_sMLjQBWn1ppoRkLxCSdFyF7RmPhLUVDtC7mfZ5jGYgVIzMR6rW2FvUIukycPGjsEWL9PiyIub_2ocOvgXxGMggV_rIjeI3j6jEQ-BGTLfWzeVOPTBkk2vcucyd9QgZBCPe3A",
        "HTTP_FORWARDED": "for=92.169.228.162;host=echo-api.apps.cluster-l5mt5.l5mt5.sandbox1873.opentlc.com;proto=https, for=92.169.228.162;host=echo-api.apps.cluster-l5mt5.l5mt5.sandbox1873.opentlc.com;proto=https",
        "HTTP_HOST": "echo-api.3scale.net",
        "HTTP_UBER_TRACE_ID": "3571087cfb4b59b3:f187ff011680923e:3571087cfb4b59b3:1, 3571087cfb4b59b3:a125cfd990a3369e:e1ebfb5bbe241762:1",
        "HTTP_USER_AGENT": "HTTPie/3.2.1,HTTPie/3.2.1",
        "HTTP_USER_KEY": "fb61a7d34e82c83b029216a3ca2e24e6, fb61a7d34e82c83b029216a3ca2e24e6",
        "HTTP_VERSION": "HTTP/1.1",
        "HTTP_X_ENVOY_EXPECTED_RQ_TIMEOUT_MS": "15000",
        "HTTP_X_ENVOY_EXTERNAL_ADDRESS": "3.74.67.248",
        "HTTP_X_FORWARDED_FOR": "92.169.228.162,92.169.228.162,3.74.67.248",
        "HTTP_X_FORWARDED_HOST": "echo-api.apps.cluster-l5mt5.l5mt5.sandbox1873.opentlc.com, echo-api.apps.cluster-l5mt5.l5mt5.sandbox1873.opentlc.com",
        "HTTP_X_FORWARDED_PORT": "443, 443",
        "HTTP_X_FORWARDED_PROTO": "https",
        "HTTP_X_REQUEST_ID": "5dea4e96-5768-4ec2-a96a-de3e0559d80c"
    },
    "method": "GET",
    "path": "/demo",
    "uuid": "98621a69-0fa8-4bf6-8c11-7e9ae140f9fd"
}
```

If you enabled [_OpenTracing_](https://access.redhat.com/documentation/en-us/red_hat_3scale_api_management/2.12/html-single/installing_3scale/index#configuring-opentracing-with-the-threescale-operator) on your _Red Hat 3scale API Management_ platform and used the same [_Jaeger_](https://www.jaegertracing.io/) collector, you can observe spans similar to the following:
![OpenTracing](./images/OpenTracing.png)

## Related Guides

- OpenShift ([guide](https://quarkus.io/guides/deploying-to-openshift)): Generate OpenShift resources from annotations
- OpenID Connect Client ([guide](https://quarkus.io/guides/security-openid-connect-client)): Get and refresh access tokens from OpenID Connect providers
- Camel MicroProfile Health ([guide](https://access.redhat.com/documentation/en-us/red_hat_integration/2.latest/html/camel_extensions_for_quarkus_reference/extensions-microprofile-health)): Expose Camel health checks via MicroProfile Health
- Camel MicroProfile Metrics ([guide](https://access.redhat.com/documentation/en-us/red_hat_integration/2.latest/html/camel_extensions_for_quarkus_reference/extensions-microprofile-metrics)): Expose metrics from Camel routes
- Camel Bean Validator ([guide](https://access.redhat.com/documentation/en-us/red_hat_integration/2.latest/html/camel_extensions_for_quarkus_reference/extensions-bean-validator)): Validate the message body using the Java Bean Validation API
- YAML Configuration ([guide](https://quarkus.io/guides/config#yaml)): Use YAML to configure your Quarkus application
- RESTEasy JAX-RS ([guide](https://quarkus.io/guides/rest-json)): REST endpoint framework implementing JAX-RS and more
- Camel Bean ([guide](https://access.redhat.com/documentation/en-us/red_hat_integration/2.latest/html/camel_extensions_for_quarkus_reference/extensions-bean)): Invoke methods of Java beans
- Kubernetes Config ([guide](https://quarkus.io/guides/kubernetes-config)): Read runtime configuration from Kubernetes ConfigMaps and Secrets
- Camel OpenTelemetry ([guide](https://access.redhat.com/documentation/en-us/red_hat_integration/2.latest/html/camel_extensions_for_quarkus_reference/extensions-opentelemetry)): Distributed tracing using OpenTelemetry
- Camel Netty HTTP ([guide](https://camel.apache.org/camel-quarkus/latest/reference/extensions/netty-http.html)): Netty HTTP server and client using the Netty 4.x

## Provided Code

### YAML Config

Configure your application with YAML

[Related guide section...](https://quarkus.io/guides/config-reference#configuration-examples)

The Quarkus application configuration is located in `src/main/resources/application.yml`.
