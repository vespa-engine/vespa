// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

/**
 * Signals that supplied JSON is invalid
 *
 * @author bjorncs
 */
public class JsonParseException extends FeedException {

    public JsonParseException(String message) { super(message); }

    public JsonParseException(String message, Throwable cause) { super(message, cause); }

}
