package org.jeannyil.routes;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.enterprise.context.ApplicationScoped;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class CamelProxyRoute extends RouteBuilder {

    private static String logName = CamelProxyRoute.class.getName();

    @ConfigProperty(name = "camel-proxy-service.keystore.mount-path")
    String keystoreMountPath;

    @Override
    public void configure() throws Exception {

        // Catch unexpected exceptions
		onException(java.lang.Exception.class)
            .handled(false)
            .maximumRedeliveries(0)
            .log(LoggingLevel.ERROR, logName, ">>> ${routeId} - Caught exception: ${exception.stacktrace}")
        ;

        final RouteDefinition from;
        if (Files.exists(keystorePath())) {
            from = from("netty-http:proxy://0.0.0.0:{{camel-proxy-service.port.secure}}"
                        + "?ssl=true&keyStoreFile={{camel-proxy-service.keystore.mount-path}}/keystore.p12"
                        + "&passphrase={{camel-proxy-service.keystore.passphrase}}"
                        + "&trustStoreFile={{camel-proxy-service.keystore.mount-path}}/keystore.p12");
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
    
}
