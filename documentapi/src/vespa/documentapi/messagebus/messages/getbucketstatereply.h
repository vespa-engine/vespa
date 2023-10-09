// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentreply.h"
#include "documentstate.h"

namespace documentapi {

class GetBucketStateReply : public DocumentReply {
private:
    std::vector<DocumentState> _state;

public:
    /**
     * Constructs a new reply with no content.
     */
    GetBucketStateReply();

    /**
     * Constructs a new reply with initial content. This method takes ownership of the provided state, i.e. it
     * swaps the content of the argument into self.
     *
     * @param state The state to swap.
     */
    GetBucketStateReply(std::vector<DocumentState> &state);
    ~GetBucketStateReply();

    /**
     * Sets the bucket state of this by swapping the content of the provided state object.
     *
     * @param state The state to swap.
     */
    void setBucketState(std::vector<DocumentState> &state) { _state.swap(state); }

    /**
     * Returns the bucket state contained in this.
     *
     * @return The state object.
     */
    std::vector<DocumentState> &getBucketState() { return _state; }

    /**
     * Returns the bucket state contained in this.
     *
     * @return The state object.
     */
    const std::vector<DocumentState> &getBucketState() const { return _state; }

    string toString() const override { return "getbucketstatereply"; }
};

} // documentapi

