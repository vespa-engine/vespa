// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import com.yahoo.yolean.Exceptions;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * The result of a health lookup.
 *
 * @author hakon
 */
public class HealthInfo {
    public static final String UP_STATUS_CODE = "up";

    private final Optional<Exception> exception;
    private final OptionalInt httpStatusCode;
    private final Optional<String> healthStatusCode;

    static HealthInfo fromException(Exception exception) {
        return new HealthInfo(Optional.of(exception), OptionalInt.empty(), Optional.empty());
    }

    static HealthInfo fromBadHttpStatusCode(int httpStatusCode) {
        return new HealthInfo(Optional.empty(), OptionalInt.of(httpStatusCode), Optional.empty());
    }

    static HealthInfo fromHealthStatusCode(String healthStatusCode) {
        return new HealthInfo(Optional.empty(), OptionalInt.empty(), Optional.of(healthStatusCode));
    }

    private HealthInfo(Optional<Exception> exception, OptionalInt httpStatusCode, Optional<String> healthStatusCode) {
        this.exception = exception;
        this.httpStatusCode = httpStatusCode;
        this.healthStatusCode = healthStatusCode;
    }

    public boolean isHealthy() {
        return healthStatusCode.map(UP_STATUS_CODE::equals).orElse(false);
    }

    public Optional<String> getErrorDescription() {
        return isHealthy() ? Optional.empty() : Optional.of(toString());
    }

    @Override
    public String toString() {
        if (isHealthy()) {
            return UP_STATUS_CODE;
        } else if (healthStatusCode.isPresent()) {
            return "Bad health status code '" + healthStatusCode.get() + "'";
        } else if (exception.isPresent()) {
            return "Exception: " + Exceptions.toMessageString(exception.get());
        } else if (httpStatusCode.isPresent()) {
            return "Bad HTTP response status code " + httpStatusCode.getAsInt();
        } else {
            return "No health info available";
        }
    }
}
