// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.orchestrator;

@SuppressWarnings("serial")
public class OrchestratorNotFoundException extends OrchestratorException {
    public OrchestratorNotFoundException(String message) {
        super(message);
    }
}
