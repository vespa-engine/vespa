// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import ai.vespa.feed.client.Result.Type;

/**
 * Workaround for package-private {@link Result} constructor.
 *
 * @author bjorncs
 */
public class DryrunResult {

    private DryrunResult() {}

    public static Result create(Type type, DocumentId documentId, String resultMessage, String traceMessage) {
        return new Result(type, documentId, resultMessage, traceMessage);
    }
}
