package org.jeannyil.routes;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

import org.jeannyil.constants.DirectEndpointConstants;

/* Route that returns the error model message in JSON format

/!\ The @ApplicationScoped annotation is required for @Inject and @ConfigProperty to work in a RouteBuilder. 
	Note that the @ApplicationScoped beans are managed by the CDI container and their life cycle is thus a bit 
	more complex than the one of the plain RouteBuilder. 
	In other words, using @ApplicationScoped in RouteBuilder comes with some boot time penalty and you should 
	therefore only annotate your RouteBuilder with @ApplicationScoped when you really need it. */
public class GenerateErrorResponseRoute extends RouteBuilder {
	
	private static String logName = GenerateErrorResponseRoute.class.getName();

	@Override
	public void configure() throws Exception {
		
		/**
		 * Route that returns the error response message in JSON format
		 * The following properties are expected to be set on the incoming Camel Exchange Message if customization is needed:
		 * <br>- CamelHttpResponseCode ({@link org.apache.camel.Exchange#HTTP_RESPONSE_CODE})
		 * <br>- CamelHttpResponseText ({@link org.apache.camel.Exchange#HTTP_RESPONSE_TEXT})
		 */
		from(DirectEndpointConstants.DIRECT_GENERATE_ERROR_MESSAGE)
			.routeId("generate-error-response-route")
			.filter(simple("${header.CamelHttpResponseCode} == null")) // Defaults to 500 HTTP Code
				.setHeader(Exchange.HTTP_RESPONSE_CODE, constant(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()))
				.setHeader(Exchange.HTTP_RESPONSE_TEXT, constant(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase()))
			.end() // end filter
			.setHeader(Exchange.CONTENT_TYPE, constant(MediaType.APPLICATION_JSON))
			.setBody()
				.method("errorResponseHelper", 
						"generateErrorMessage(${headers.CamelHttpResponseCode}, ${exception.message})")
			.end()
			.marshal().json(JsonLibrary.Jackson, true)
			.convertBodyTo(String.class)
			.log(LoggingLevel.ERROR, logName, ">>> ${routeId} - OUT: headers:[${headers}] - body:[${body}]")
		;
		
	}

}