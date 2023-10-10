// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.BucketId;
import com.yahoo.document.DocumentId;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

/**
 * @author vekterli
 */
public class MessageSequencingTest {

    /*
     * Sequencing read-only operations artificially limits parallelization of such operations.
     * We do not violate linearizability by not sequencing, as it only assumes that a write
     * will become visible at some "atomic" point between sending the write and receiving an
     * ACK for it. I.e. if we have not received an ACK, we cannot guarantee operation visibility
     * either way. Sending off a read just after sending a write inherently does not satisfy
     * this requirement for visibility.
     */

    @Test
    public void get_document_message_is_not_sequenced() {
        GetDocumentMessage message = new GetDocumentMessage(new DocumentId("id:foo:bar::baz"));
        assertFalse(message.hasSequenceId());
    }

    @Test
    public void stat_bucket_message_is_not_sequenced() {
        StatBucketMessage message = new StatBucketMessage(new BucketId(16, 1), "");
        assertFalse(message.hasSequenceId());
    }

    @Test
    public void get_bucket_list_message_is_not_sequenced() {
        GetBucketListMessage message = new GetBucketListMessage(new BucketId(16, 1));
        assertFalse(message.hasSequenceId());
    }

}
