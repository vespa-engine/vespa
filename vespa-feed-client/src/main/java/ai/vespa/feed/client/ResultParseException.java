// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

/**
 * Signals that the client was unable to parse the result/response from container
 *
 * @author bjorncs
 */
public class ResultParseException extends FeedException {

    public ResultParseException(DocumentId documentId, String message) { super(documentId, message); }

    public ResultParseException(DocumentId documentId, Throwable cause) { super(documentId, cause); }
}
