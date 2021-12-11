// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import ai.vespa.feed.client.FeedException;

/**
 * Signals that supplied JSON for a document/operation is invalid
 *
 * @author bjorncs
 */
public class OperationParseException extends FeedException {

    public OperationParseException(String message) { super(message); }

    public OperationParseException(String message, Throwable cause) { super(message, cause); }

}
