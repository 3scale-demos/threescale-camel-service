package org.jeannyil.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.quarkus.runtime.annotations.RegisterForReflection;

@JsonInclude(JsonInclude.Include.NON_NULL) // Ensure no null fields are serialized
@JsonPropertyOrder({ "code", "message" }) // Specify property order in JSON
@RegisterForReflection // Ensure the class is available for Quarkus reflection at runtime
public class ErrorMessage {

    private final int code;
    private final String message;

    // JsonCreator ensures all fields are required during deserialization
    @JsonCreator
    public ErrorMessage(
        @JsonProperty(value = "code", required = true) int code,
        @JsonProperty(value = "message", required = true) String message
    ) {
        this.code = code;
        this.message = message;
    }

    // Getters
    @JsonProperty("code")
    public int getCode() {
        return code;
    }

    @JsonProperty("message")
    public String getMessage() {
        return message;
    }

    // toString() for easier logging or debugging
    @Override
    public String toString() {
        return "ErrorModel{" +
                "code='" + code + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
