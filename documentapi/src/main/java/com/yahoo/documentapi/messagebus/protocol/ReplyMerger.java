// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.collections.Tuple2;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.Reply;

/**
 * Encapsulated logic for merging replies from 1-n related DocumentProtocol messages.
 * For internal use only. Not multithread safe.
 */
final class ReplyMerger {

    private Reply successReply = null;
    private int successIndex = -1;
    private Reply error = null;
    private Reply ignore = null;

    public void merge(int i, Reply r) {
        if (r.hasErrors()) {
            mergeAllReplyErrors(r);
        } else {
            updateStateWithSuccessfulReply(i, r);
        }
    }

    private boolean resourceWasFound(Reply r) {
        if (r instanceof RemoveDocumentReply) {
            return ((RemoveDocumentReply) r).wasFound();
        }
        if (r instanceof UpdateDocumentReply) {
            return ((UpdateDocumentReply) r).wasFound();
        }
        if (r instanceof GetDocumentReply) {
            return ((GetDocumentReply) r).getLastModified() > 0;
        }
        return false;
    }

    private boolean replyIsBetterThanCurrent(Reply r) {
        return resourceWasFound(r) && !resourceWasFound(successReply);
    }

    private void updateStateWithSuccessfulReply(int i, Reply r) {
        if (successReply == null || replyIsBetterThanCurrent(r)) {
            setCurrentBestReply(i, r);
        }
    }

    private void setCurrentBestReply(int i, Reply r) {
        successReply = r;
        successIndex = i;
    }

    private void mergeAllReplyErrors(Reply r) {
        if (handleReplyWithOnlyIgnoredErrors(r)) {
            return;
        }
        if (error == null) {
            error = r;
        }
        else if (mostSevereErrorCode(r) > mostSevereErrorCode(error)) {
            error.getErrors().forEach(r::addError);
            error = r;
        }
        else {
            r.getErrors().forEach(error::addError);
        }
    }

    private static int mostSevereErrorCode(Reply reply) {
        return reply.getErrors().mapToInt(Error::getCode).max()
                .orElseThrow(() -> new IllegalArgumentException(reply + " has no errors"));
    }

    private boolean handleReplyWithOnlyIgnoredErrors(Reply r) {
        if (DocumentProtocol.hasOnlyErrorsOfType(r, DocumentProtocol.ERROR_MESSAGE_IGNORED)) {
            if (ignore == null) {
                ignore = new EmptyReply();
            }
            ignore.addError(r.getError(0));
            return true;
        }
        return false;
    }

    private boolean shouldReturnErrorReply() {
        return (error != null || (ignore != null && successReply == null));
    }

    private Tuple2<Integer, Reply> createMergedErrorReplyResult() {
        if (error != null) {
            return new Tuple2<>(null, error);
        }
        if (ignore != null && successReply == null) {
            return new Tuple2<>(null, ignore);
        }
        throw new IllegalStateException("createMergedErrorReplyResult called without error");
    }

    private boolean successfullyMergedAtLeastOneReply() {
        return successReply != null;
    }

    private Tuple2<Integer, Reply> createEmptyReplyResult() {
        return new Tuple2<>(null, new EmptyReply());
    }

    public Tuple2<Integer, Reply> mergedReply() {
        if (shouldReturnErrorReply()) {
            return createMergedErrorReplyResult();
        } else if (!successfullyMergedAtLeastOneReply()) {
            return createEmptyReplyResult();
        }
        return new Tuple2<>(successIndex, successReply);
    }

}
