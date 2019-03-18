// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitBranch;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitCommit;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitRepository;
import com.yahoo.vespa.hosted.controller.api.identifiers.RevisionId;
import com.yahoo.vespa.hosted.controller.api.identifiers.ScrewdriverId;

import java.net.URI;
import java.util.List;

/**
 * @author mortent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InstanceInformation {
    public List<URI> serviceUrls;
    public URI nodes;
    public URI yamasUrl;
    public RevisionId revision;
    public Long deployTimeEpochMs;
    public Long expiryTimeEpochMs;

    public ScrewdriverId screwdriverId;
    public GitRepository gitRepository;
    public GitBranch gitBranch;
    public GitCommit gitCommit;
}
