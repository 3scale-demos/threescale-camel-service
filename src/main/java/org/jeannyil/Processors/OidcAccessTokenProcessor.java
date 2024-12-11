package org.jeannyil.processors;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.Tokens;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.vertx.core.http.HttpHeaders;

@ApplicationScoped
@Named("oidcAccessTokenProcessor")
@RegisterForReflection // Lets Quarkus register this class for reflection during the native build
public class OidcAccessTokenProcessor implements Processor {

    @Inject
    OidcClient oidcClient;

    volatile Tokens currentTokens;

    @PostConstruct
    public void init() {
        currentTokens = oidcClient.getTokens().await().indefinitely();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Tokens tokens = currentTokens;
        if (tokens.isAccessTokenExpired()) {
            tokens = oidcClient.getTokens().await().indefinitely();
            currentTokens = tokens;
        }
        exchange.getIn().setHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer " + tokens.getAccessToken());
    }
    
}