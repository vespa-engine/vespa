// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitBranch;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitCommit;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitRepository;

/**
 * Represents the response from application submit request
 *
 * @author freva
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubmitResult {

    public String version;

    @Override
    public String toString() {
        return "SubmitResult{" +
                "version='" + version + '\'' +
                '}';
    }
}
