// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.json;

/**
 * @author freva
 */
public class InvalidJsonException extends IllegalArgumentException {
    public InvalidJsonException(String message) {
        super(message);
    }
}
