// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a reply to a {@link GetBucketStateMessage}. It contains the state of the bucket id requested by the message.
 *
 * @author Simon Thoresen Hult
 */
public class GetBucketStateReply extends DocumentReply {

    private List<DocumentState> state;

    /**
     * Constructs a new reply with no content.
     */
    public GetBucketStateReply() {
        super(DocumentProtocol.REPLY_GETBUCKETSTATE);
        state = new ArrayList<DocumentState>();
    }

    /**
     * Constructs a new reply with initial content.
     *
     * @param state The state to set.
     */
    public GetBucketStateReply(List<DocumentState> state) {
        super(DocumentProtocol.REPLY_GETBUCKETSTATE);
        this.state = state;
    }

    /**
     * Sets the bucket state of this.
     *
     * @param state The state to set.
     */
    public void setBucketState(List<DocumentState> state) {
        this.state = state;
    }

    /**
     * Returns the bucket state contained in this.
     *
     * @return The state object.
     */
    public List<DocumentState> getBucketState() {
        return state;
    }
}
