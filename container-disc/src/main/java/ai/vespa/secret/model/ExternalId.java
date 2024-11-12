// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.secret.model;

/**
 * @author mortent
 */
public record ExternalId(String value) {

    public ExternalId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ExternalId cannot be null or empty");
        }
    }

    public static ExternalId of(String value) {
        return new ExternalId(value);
    }
}
