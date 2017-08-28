// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api;

import com.yahoo.vespa.hosted.controller.api.identifiers.RevisionId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Log;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.PrepareResponse;

import java.util.List;

/**
 * @author Oyvind Gronnesby
 */
public class ActivateResult {

    private final RevisionId revisionId;
    private final List<Log> messages;
    private final PrepareResponse prepareResponse;

    public ActivateResult(RevisionId revisionId, List<Log> messages, PrepareResponse prepareResponse) {
        this.revisionId = revisionId;
        this.messages = messages;
        this.prepareResponse = prepareResponse;
    }

    public RevisionId getRevisionId() {
        return revisionId;
    }

    public List<Log> getMessages() {
        return messages;
    }

    public PrepareResponse getPrepareResponse() {
        return prepareResponse;
    }

}
