package org.jeannyil.routes;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import jakarta.ws.rs.core.Response;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jeannyil.constants.DirectEndpointConstants;

@ApplicationScoped
public class CamelProxyRoute extends RouteBuilder {

    private static String logName = CamelProxyRoute.class.getName();

    @ConfigProperty(name = "camel-proxy-service.keystore.mount-path")
    String keystoreMountPath;

    @Override
    public void configure() throws Exception {

        // Catch unexpected exceptions
        onException(java.lang.Exception.class)
            .handled(true)
            .maximumRedeliveries(0)
            .log(LoggingLevel.ERROR, logName, ">>> ${routeId} - Caught exception: ${exception.stacktrace}")
            .to(DirectEndpointConstants.DIRECT_GENERATE_ERROR_MESSAGE)
        ;

        // OIDC Client and IllegalArgument Exceptions
        onException(io.quarkus.oidc.client.OidcClientException.class, java.lang.IllegalArgumentException.class)
            .handled(true)
            .maximumRedeliveries(0)
            .log(LoggingLevel.ERROR, logName, ">>> ${routeId} - Caught exception: ${exception.stacktrace}")
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(Response.Status.UNAUTHORIZED.getStatusCode()))
            .setHeader(Exchange.HTTP_RESPONSE_TEXT, constant(Response.Status.UNAUTHORIZED.getReasonPhrase()))
            .to(DirectEndpointConstants.DIRECT_GENERATE_ERROR_MESSAGE)
        ;

        final RouteDefinition from;
        if (Files.exists(keystorePath())) {
            from = from("netty-http:proxy://0.0.0.0:{{camel-proxy-service.port.secure}}"
                        + "?ssl=true&keyStoreFile=#keystoreFile"
                        + "&passphrase={{camel-proxy-service.keystore.passphrase}}"
                        + "&trustStoreFile=#keystoreFile");
        } else {
            from = from("netty-http:proxy://0.0.0.0:{{camel-proxy-service.port.nonsecure}}");
        }

        from
            .routeId("camel-reverseproxy-route")
            .log(LoggingLevel.INFO, logName, "Incoming headers: ${headers}")
            // Add Authorization header containing the OIDC Access Token
            .process("oidcAccessTokenProcessor")
            .log(LoggingLevel.INFO, logName, "Headers after processor: ${headers}")
            // Call backend service
            .toD("netty-http:"
                + "${headers." + Exchange.HTTP_SCHEME + "}://"
                + "${headers." + Exchange.HTTP_HOST + "}:"
                + "${headers." + Exchange.HTTP_PORT + "}"
                + "${headers." + Exchange.HTTP_PATH + "}"
                + "?synchronous=true")
        ;
        
    }

    Path keystorePath() {
        return Path.of(keystoreMountPath, "keystore.p12");
    }

    @Named("keystoreFile")
    File getKeystoreFile() {
        return keystorePath().toFile();
    }
    
}
