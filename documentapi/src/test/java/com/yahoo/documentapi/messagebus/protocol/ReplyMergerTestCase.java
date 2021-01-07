// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.collections.Tuple2;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.Reply;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ReplyMergerTestCase {

    private ReplyMerger merger;

    @Before
    public void setUp() {
        merger = new ReplyMerger();
    }

    @Test
    public void mergingGenericRepliesWithNoErrorsPicksFirstReply() {
        Reply r1 = new EmptyReply();
        Reply r2 = new EmptyReply();
        Reply r3 = new EmptyReply();
        merger.merge(0, r1);
        merger.merge(1, r2);
        merger.merge(2, r3);
        Tuple2<Integer, Reply> ret = merger.mergedReply();

        assertEquals(0, ret.first.intValue());
        assertSame(r1, ret.second);
    }

    @Test
    public void mergingSingleReplyWithOneErrorReturnsSameReplyWithError() {
        Reply r1 = new EmptyReply();
        Error error = new Error(1234, "oh no!");
        r1.addError(error);
        merger.merge(0, r1);
        Tuple2<Integer, Reply> ret = merger.mergedReply();

        assertNull(ret.first);
        assertSame(r1, ret.second);
        assertThatErrorsMatch(new Error[] { error }, ret);
    }

    @Test
    public void mergingSingleReplyWithMultipleErrorsReturnsSameReplyWithAllErrors() {
        Reply r1 = new EmptyReply();
        Error errors[] = new Error[] {
                new Error(1234, "oh no!"), new Error(4567, "oh dear!"),
        };
        r1.addError(errors[0]);
        r1.addError(errors[1]);
        merger.merge(0, r1);
        Tuple2<Integer, Reply> ret = merger.mergedReply();

        assertNull(ret.first);
        assertSame(r1, ret.second);
        assertThatErrorsMatch(errors, ret);
    }

    @Test
    public void mergingMultipleRepliesWithMultipleErrorsReturnsMostSevereReplyWithAllErrors() {
        Reply r1 = new EmptyReply();
        Reply r2 = new EmptyReply();
        Error errors[] = new Error[] {
                new Error(1234, "oh no!"), new Error(4567, "oh dear!"), new Error(678, "omg!"),
        };
        r1.addError(errors[0]);
        r1.addError(errors[1]);
        r2.addError(errors[2]);
        merger.merge(0, r1);
        merger.merge(1, r2);
        Tuple2<Integer, Reply> ret = merger.mergedReply();

        assertNull(ret.first);
        assertSame(r1, ret.second);
        assertNotSame(r2, ret.second);
        assertThatErrorsMatch(errors, ret);
    }

    @Test
    public void returnIgnoredReplyWhenAllRepliesHaveOnlyIgnoredErrors() {
        Reply r1 = new EmptyReply();
        Reply r2 = new EmptyReply();
        Error errors[] = new Error[] {
                new Error(DocumentProtocol.ERROR_MESSAGE_IGNORED, "oh no!"),
                new Error(DocumentProtocol.ERROR_MESSAGE_IGNORED, "oh dear!"),
                new Error(DocumentProtocol.ERROR_MESSAGE_IGNORED, "omg!"),
        };
        r1.addError(errors[0]);
        r1.addError(errors[1]);
        r2.addError(errors[2]);

        merger.merge(0, r1);
        merger.merge(1, r2);
        Tuple2<Integer, Reply> ret = merger.mergedReply();
        assertNull(ret.first);
        assertNotSame(r1, ret.second);
        assertNotSame(r2, ret.second);
        // Only first ignore error from each reply
        assertThatErrorsMatch(new Error[]{ errors[0], errors[2] }, ret);
    }

    @Test
    public void successfulReplyTakesPrecedenceOverIgnoredReplyWhenNoErrors() {
        Reply r1 = new EmptyReply();
        Reply r2 = new EmptyReply();
        Error errors[] = new Error[] {
                new Error(DocumentProtocol.ERROR_MESSAGE_IGNORED, "oh no!"),
        };
        r1.addError(errors[0]);
        merger.merge(0, r1);
        merger.merge(1, r2);
        Tuple2<Integer, Reply> ret = merger.mergedReply();
        assertEquals(1, ret.first.intValue());
        assertSame(r2, ret.second);
        // Only first ignore error from each reply
        assertThatErrorsMatch(new Error[]{ }, ret);
    }

    @Test
    public void nonIgnoredErrorTakesPrecedence() {
        Reply r1 = new EmptyReply();
        Reply r2 = new EmptyReply();
        Error errors[] = new Error[] {
                new Error(DocumentProtocol.ERROR_MESSAGE_IGNORED, "oh no!"),
                new Error(DocumentProtocol.ERROR_ABORTED, "kablammo!"),
                new Error(DocumentProtocol.ERROR_MESSAGE_IGNORED, "omg!"),
        };
        r1.addError(errors[0]);
        r1.addError(errors[1]);
        r2.addError(errors[2]);

        merger.merge(0, r1);
        merger.merge(1, r2);
        Tuple2<Integer, Reply> ret = merger.mergedReply();
        assertNull(ret.first);
        assertSame(r1, ret.second);
        assertNotSame(r2, ret.second);
        // All errors from replies with errors are included, not those that
        // are fully ignored.
        assertThatErrorsMatch(new Error[]{ errors[0], errors[1] }, ret);
    }

    @Test
    public void returnRemoveDocumentReplyWhereDocWasFound() {
        RemoveDocumentReply r1 = new RemoveDocumentReply();
        RemoveDocumentReply r2 = new RemoveDocumentReply();
        RemoveDocumentReply r3 = new RemoveDocumentReply();
        r1.setWasFound(false);
        r2.setWasFound(true);
        r3.setWasFound(false);

        merger.merge(0, r1);
        merger.merge(1, r2);
        merger.merge(2, r3);
        Tuple2<Integer, Reply> ret = merger.mergedReply();
        assertEquals(1, ret.first.intValue());
        assertSame(r2, ret.second);
    }

    @Test
    public void returnFirstRemoveDocumentReplyIfNoDocsWereFound() {
        RemoveDocumentReply r1 = new RemoveDocumentReply();
        RemoveDocumentReply r2 = new RemoveDocumentReply();
        r1.setWasFound(false);
        r2.setWasFound(false);

        merger.merge(0, r1);
        merger.merge(1, r2);
        Tuple2<Integer, Reply> ret = merger.mergedReply();
        assertEquals(0, ret.first.intValue());
        assertSame(r1, ret.second);
    }

    @Test
    // TODO jonmv: This seems wrong, and is probably a consequence of TAS being implemented after reply merging.
    public void returnErrorDocumentReplyWhereDocWasFoundWhichIsProbablyWrong() {
        Error e1 = new Error(DocumentProtocol.ERROR_TEST_AND_SET_CONDITION_FAILED, "fail");
        UpdateDocumentReply r1 = new UpdateDocumentReply();
        UpdateDocumentReply r2 = new UpdateDocumentReply();
        UpdateDocumentReply r3 = new UpdateDocumentReply();
        r1.addError(e1); // return error
        r2.setWasFound(true);
        r3.setWasFound(true);

        merger.merge(0, r1);
        merger.merge(1, r2);
        merger.merge(2, r3);
        Tuple2<Integer, Reply> ret = merger.mergedReply();
        assertNull(ret.first);
        assertSame(r1, ret.second);
        assertThatErrorsMatch(new Error[] { e1 }, ret);
    }

    @Test
    public void returnUpdateDocumentReplyWhereDocWasFound() {
        UpdateDocumentReply r1 = new UpdateDocumentReply();
        UpdateDocumentReply r2 = new UpdateDocumentReply();
        UpdateDocumentReply r3 = new UpdateDocumentReply();
        r1.setWasFound(false);
        r2.setWasFound(true); // return first reply
        r3.setWasFound(true);

        merger.merge(0, r1);
        merger.merge(1, r2);
        merger.merge(2, r3);
        Tuple2<Integer, Reply> ret = merger.mergedReply();
        assertEquals(1, ret.first.intValue());
        assertSame(r2, ret.second);
    }

    @Test
    public void returnGetDocumentReplyWhereDocWasFound() {
        GetDocumentReply r1 = new GetDocumentReply(null);
        GetDocumentReply r2 = new GetDocumentReply(null);
        GetDocumentReply r3 = new GetDocumentReply(null);
        r2.setLastModified(12345L);

        merger.merge(0, r1);
        merger.merge(1, r2);
        merger.merge(2, r3);
        Tuple2<Integer, Reply> ret = merger.mergedReply();
        assertEquals(1, ret.first.intValue());
        assertSame(r2, ret.second);
    }

    @Test
    public void mergingZeroRepliesReturnsDefaultEmptyReply() {
        Tuple2<Integer, Reply> ret = merger.mergedReply();
        assertNull(ret.first);
        assertTrue(ret.second instanceof EmptyReply);
        assertThatErrorsMatch(new Error[]{}, ret);
    }

    private void assertThatErrorsMatch(Error[] errors, Tuple2<Integer, Reply> ret) {
        assertEquals(errors.length, ret.second.getNumErrors());
        for (int i = 0; i < ret.second.getNumErrors(); ++i) {
            assertEquals(errors[i].getCode(), ret.second.getError(i).getCode());
            assertEquals(errors[i].getMessage(), ret.second.getError(i).getMessage());
        }
    }

}
