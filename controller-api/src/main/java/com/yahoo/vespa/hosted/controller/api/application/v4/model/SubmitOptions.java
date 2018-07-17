// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitBranch;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitCommit;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitRepository;

/**
 * Additional options to be sent along the application package and the application test package
 * when submitting an application to the controller
 *
 * @author freva
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubmitOptions {

    public GitRepository gitRepository;
    public GitBranch gitBranch;
    public GitCommit gitCommit;

    @Override
    public String toString() {
        return "SubmitOptions{" +
                "gitRepository=" + gitRepository +
                ", gitBranch=" + gitBranch +
                ", gitCommit=" + gitCommit +
                '}';
    }
}
