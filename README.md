# threescale-camel-service

This project leverages [**Red Hat build of Quarkus 3.27.x**](https://docs.redhat.com/en/documentation/red_hat_build_of_quarkus/3.27), the Supersonic Subatomic Java Framework. More specifically, the project is implemented using [**Red Hat build of Apache Camel v4.14.x for Quarkus**](https://docs.redhat.com/en/documentation/red_hat_build_of_apache_camel/4.14#Red%20Hat%20build%20of%20Apache%20Camel%20for%20Quarkus).

This camel proxy service can be leveraged to configure the [_Red Hat 3scale APIcast Camel Service policy_](https://docs.redhat.com/en/documentation/red_hat_3scale_api_management/2.16/html/administering_the_api_gateway/apicast-policies#camel-service_standard-policies). 

The camel proxy service uses the OAuth2 _client credentials flow_ to retrieve an access token from _Red Hat build of Keycloak_, and then sets it in the _Authorization_ HTTP header before proxying the request to the upstream backend.

## Prerequisites

- Apache Maven 3.9.9
- JDK 21 installed with `JAVA_HOME` configured appropriately
- A running [_Red Hat build of Keycloak_](https://access.redhat.com/documentation/en-us/red_hat_build_of_keycloak) instance. The following must be configured:
    1. A confidential client with the following characteristics:
        - Client ID: `threescale-camel-service`
        - Client Protocol: `openid-connect`
        - Client authentication: `on`
        - Authentication flow: `service accounts roles (client credentials)`
    2. Replace the `client secret` in:
        - `quarkus.oidc-client.credentials.secret` property in the [`application.yml`](./src/main/resources/application.yml) file
        - `quarkus.oidc-client.credentials.secret` property of the `threescale-camel-service-secret` in the [`openshift.yml`](./src/main/kubernetes/openshift.yml) file
    3. Replace the `OIDC authorization server URL` in:
        - `quarkus.oidc-client.auth-server-url`  property in the [`application.yml`](./src/main/resources/application.yml) file
        - `quarkus.oidc-client.auth-server-url` property of the `threescale-camel-service-secret` in the [`openshift.yml`](./src/main/kubernetes/openshift.yml) file
- A running [_Red Hat OpenShift_](https://access.redhat.com/documentation/en-us/openshift_container_platform) cluster
- A running [_Red Hat 3scale API Management_](https://access.redhat.com/documentation/en-us/red_hat_3scale_api_management) platform

## Generate a Java Keystore

```shell
keytool -genkey \
  -keypass P@ssw0rd \
  -storepass P@ssw0rd \
  -alias threescale-camel-service \
  -keyalg RSA \
  -dname "CN=threescale-camel-service" \
  -validity 3600 \
  -keystore ./tls-keys/keystore.p12 -v \
  -ext san=DNS:threescale-camel-service.svc,\
DNS:threescale-camel-service.svc.cluster.local,\
DNS:threescale-camel-service.camel-quarkus.svc,\
DNS:threescale-camel-service.camel-quarkus.svc.cluster.local,\
DNS:threescale-camel-service.ceq-services-jvm.svc,\
DNS:threescale-camel-service.ceq-services-jvm.svc.cluster.local,\
DNS:threescale-camel-service.ceq-services-native.svc,\
DNS:threescale-camel-service.ceq-services-native.svc.cluster.local
```

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell
./mvnw clean compile quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev-ui.

## Packaging and running the application locally

1. Execute the following command line:
    ```shell
    ./mvnw clean package
    ```
    It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
    Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

    The application is now runnable using:
    ```shell
    java -Dquarkus.kubernetes-config.enabled=false -jar target/quarkus-app/quarkus-run.jar
    ```

    If you want to build an _über-jar_, execute the following command:
    ```shell
    ./mvnw package -Dquarkus.package.type=uber-jar
    ```

    The application, packaged as an _über-jar_, is now runnable using:
    ```shell
    java -Dquarkus.kubernetes-config.enabled=false -jar target/*-runner.jar
    ```

2. **OPTIONAL:** Creating a native executable

    You can create a native executable using the following command:

    ```shell
    ./mvnw clean package -Pnative -Dquarkus.native.native-image-xmx=7g
    ```

    >**NOTE** : The project is configured to use a container runtime for native builds. See `quarkus.native.container-build=true` in the [`application.yml`](./src/main/resources/application.yml). Also, adjust the `quarkus.native.native-image-xmx` value according to your container runtime available memory resources.

    You can then execute your native executable with: `./target/threescale-camel-service-1.0.0-runner`

    If you want to learn more about building native executables, please consult https://quarkus.io/guides/building-native-image.

    >**NOTE** : If your are on Apple Silicon and built the native image inside a Linux container (`-Dquarkus.native.container-build=true`), the result is a Linux ELF binary. macOS can’t execute Linux binaries, so launching it on macOS yields “`exec format error`”. Follow the steps below to run your Linux native binary.

    1. Build the container image of your Linux native binary:
        ```shell
        podman build -f src/main/docker/Dockerfile.native -t threescale-camel-service .
        ```
    2. Run the container:
        ```shell
        podman run --rm --name threescale-camel-service \
        -p 9443:9443,9876:9876 \
        -e QUARKUS_KUBERNETES-CONFIG_ENABLED=false \
        -e QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT=http://host.containers.internal:4317 \
        -e CAMEL-PROXY-SERVICE.KEYSTORE.MOUNT-PATH=/mnt \
        -v ./tls-keys/keystore.p12:/mnt/keystore.p12:ro \
        threescale-camel-service
        ```

3. Running Jaeger locally

    [**Jaeger**](https://www.jaegertracing.io/), a distributed tracing system for observability ([_open tracing_](https://opentracing.io/)). A simple way of starting a Jaeger tracing server is with `docker` or `podman`:
    1. Start the Jaeger tracing server:
        ```shell
        podman run --rm -e COLLECTOR_ZIPKIN_HOST_PORT=:9411 -e COLLECTOR_OTLP_ENABLED=true \
        -p 6831:6831/udp -p 6832:6832/udp \
        -p 5778:5778 -p 16686:16686 -p 4317:4317 -p 4318:4318 -p 14250:14250  -p 14268:14268 -p 14269:14269 -p 9411:9411 \
        quay.io/jaegertracing/all-in-one:latest
        ```
    2. While the server is running, browse to http://localhost:16686 to view tracing events.

4. Test locally
    ```shell
    printf 'GET https://echo-api.3scale.net/demo HTTP/1.1\r\nHost: echo-api.3scale.net\r\nAccept: */*\r\nProxy-Connection: keep-alive\r\nConnection: close\r\n\r\n' | \
    ncat --ssl localhost 9443
    ```
    Sample output:
    ```shell
    HTTP/1.1 200 OK
    content-length: 1992
    server: envoy
    vary: Origin
    x-3scale-echo-api: echo-api/1.0.3
    x-content-type-options: nosniff
    x-envoy-upstream-service-time: 0
    content-type: application/json
    connection: close

    {
        "method": "GET",
        "path": "/demo",
        "args": "",
        "body": "",
        "headers": {
            "HTTP_VERSION": "HTTP/1.1",
            "HTTP_HOST": "echo-api.3scale.net",
            "HTTP_ACCEPT": "*/*,*/*",
            "CONTENT_LENGTH": "0",
            "HTTP_AUTHORIZATION": "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI...truncated...",
            "HTTP_TRACEPARENT": "00-280e6e63cfd60f55d263c7a7c20ebc42-f2fe175e72c4ea37-01",
            "HTTP_X_FORWARDED_FOR": "***.***.***.***",
            "HTTP_X_FORWARDED_PROTO": "https",
            "HTTP_X_ENVOY_EXTERNAL_ADDRESS": "***.***.***.***",
            "HTTP_X_REQUEST_ID": "582d6675-9e46-4ae2-95cd-bc36a36e44ab",
            "HTTP_X_ENVOY_EXPECTED_RQ_TIMEOUT_MS": "15000"
        },
        "uuid": "4a4b95f2-1602-4617-b891-510dad0ebeae"
    }
    ```

    >**INFO**: The camel service listening on port `9443` proxies the HTTP request to the `echo-api.3scale.net` server. You should see similar log lines as the following in the camel service proxy logs:
    ```log
    INFO  [or.je.ro.CamelProxyRoute] Incoming headers: {Accept=*/*, CamelHttpHost=echo-api.3scale.net, CamelHttpMethod=GET, CamelHttpPath=/demo, CamelHttpPort=443, CamelHttpScheme=https, ...}
    INFO  [or.je.ro.CamelProxyRoute] Headers after processor: {Accept=*/*, authorization=Bearer eyJhbGci...truncated..., CamelHttpHost=echo-api.3scale.net, CamelHttpMethod=GET, ...}
    INFO  [or.ap.ca.co.ne.ht.HttpClientInitializerFactory] Created SslContext javax.net.ssl.SSLContext@e375928
    ```

## Packaging and running the application on Red Hat OpenShift

### Pre-requisites

- Access to a [Red Hat OpenShift](https://access.redhat.com/documentation/en-us/openshift_container_platform) cluster v4
- User has self-provisioner privilege or has access to a working OpenShift project
- **OPTIONAL**: [**Jaeger**](https://www.jaegertracing.io/), a distributed tracing system for observability ([_open tracing_](https://opentracing.io/)).
- **For native mode only**: A Linux X86_64 operating system or an OCI (Open Container Initiative) compatible container runtime, such as Podman or Docker is required.

### Common setup steps

1. Login to the OpenShift cluster
    ```shell
    oc login ...
    ```
2. Create an OpenShift project or use your existing OpenShift project. For instance:
    - JVM mode:
        ```shell
        oc new-project ceq-services-jvm --display-name="Red Hat build of Apache Camel for Quarkus Apps - JVM Mode"
        ```
    - Native mode:
        ```shell
        oc new-project ceq-services-native --display-name="Red Hat build of Apache Camel for Quarkus Apps - Native Mode"
        ```
3. Create secret containing the keystore
    ```shell
    oc create secret generic threescale-camel-service-keystore-secret \
    --from-file=keystore.p12=./tls-keys/keystore.p12
    ```
4. Adjust the `quarkus.otel.exporter.otlp.endpoint` property of the `threescale-camel-service-secret` in the [`openshift.yml`](./src/main/kubernetes/openshift.yml) file according to your OpenShift environment and where you installed the [_Jaeger_](https://www.jaegertracing.io/) server.

### Deploy using the S2I binary workflow

- **JVM mode**:
    ```shell
    ./mvnw clean package -Dquarkus.openshift.deploy=true
    ```
- **Native mode**:
    ```shell
    ./mvnw clean package -Pnative \
    -Dquarkus.openshift.deploy=true
    ```

## How to configure the _APICast Camel Service_ policy to use this service

### Pre-requisite

- An API Product configured in _Red Hat 3scale API Management_. For instance, the sample `Echo API` can be used.

### Instructions

1. Add and configure the [_APICast Camel Service_](https://docs.redhat.com/en/documentation/red_hat_3scale_api_management/2.16/html/administering_the_api_gateway/apicast-policies#camel-service_standard-policies) policy on the API Product
    - Reference: https://docs.redhat.com/en/documentation/red_hat_3scale_api_management/2.16/html/administering_the_api_gateway/transform-with-policy-extension_3scale#configure-apicast-policy-extension-in-fuse_3scale
2. Beware of the following note:
    > **NOTE**: 
    You cannot use `curl` (or any other HTTP client) to test the Camel HTTP proxy directly because the proxy does not support HTTP tunneling using the `CONNECT` method. When using HTTP tunneling with `CONNECT`, the transport is end-to-end encrypted, which does not allow the Camel HTTP proxy to mediate the payload. 
    You may test this with 3scale, which implements this as if proxying via HTTP but establishes a new TLS session towards the Camel application. If you need to perform integration tests against the Camel application you need to use a custom HTTP client. 
    You can use something like: 
    `printf 'GET https://<backend url> HTTP/1.1\r\nHost: <backend_host>\r\nAccept: */*\r\nProxy-Connection: keep-alive\r\nConnection: close\r\n\r\n' | ncat --ssl <camel_proxy_app_host> <camel_proxy_app_port>`

Below is a screenshot of the [_Camel Service_](https://docs.redhat.com/en/documentation/red_hat_3scale_api_management/2.16/html/administering_the_api_gateway/apicast-policies#camel-service_standard-policies) policy configuration:

![APICast Camel Service Policy](./images/CamelServicePolicy.png)

Below is a sample test where you can notice the `Authorization` HTTP header added and populated with the retrieved OpenID Connect access token (`HTTP_AUTHORIZATION` header in the `Echo API` response):

```shell
http -v 'https://echo-api.apps.cluster-l5mt5.l5mt5.sandbox1873.opentlc.com:443/demo' user_key:fb61a7d34e82c83b029216a3ca2e24e6
```
```shell
GET /demo HTTP/1.1
Accept: */*
Accept-Encoding: gzip, deflate
Connection: keep-alive
Host: echo-api.apps.cluster-l5mt5.l5mt5.sandbox1873.opentlc.com:443
User-Agent: HTTPie/3.2.1
user_key: fb61a7d34e82c83b029216a3ca2e24e6

HTTP/1.1 200 OK
content-type: application/json
server: envoy
x-3scale-echo-api: echo-api/1.0.3
...

{
    "method": "GET",
    "path": "/demo",
    "headers": {
        "HTTP_HOST": "echo-api.3scale.net",
        "HTTP_AUTHORIZATION": "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI...truncated...",
        "HTTP_USER_KEY": "fb61a7d34e82c83b029216a3ca2e24e6, fb61a7d34e82c83b029216a3ca2e24e6",
        ...
    },
    "uuid": "98621a69-0fa8-4bf6-8c11-7e9ae140f9fd"
}
```
