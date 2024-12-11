package org.jeannyil.beans;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import org.jeannyil.models.ErrorMessage;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * 
 * Error Response helper bean 
 *
 */
@ApplicationScoped
@Named("errorResponseHelper")
@RegisterForReflection // Lets Quarkus register this class for reflection during the native build
public class ErrorResponseHelper {
	
	/**
	 * Generates the ErrorResponseMessage
	 * @param erroCode
	 * @param errorMessage
	 * @return ErrorModel
	 */
	public ErrorMessage generateErrorMessage(int errorCode, String errorMessage) {
		return new ErrorMessage(errorCode, errorMessage);
	}

}
