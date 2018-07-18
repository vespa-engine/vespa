// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitBranch;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitCommit;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitRepository;

import java.util.Objects;

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

    public static SubmitOptions from(String repository, String branch, String commit) {
        SubmitOptions options = new SubmitOptions();
        options.gitRepository = new GitRepository(repository);
        options.gitBranch = new GitBranch(branch);
        options.gitCommit = new GitCommit(commit);
        return options;
    }

    @Override
    public String toString() {
        return "SubmitOptions{" +
                "gitRepository=" + gitRepository +
                ", gitBranch=" + gitBranch +
                ", gitCommit=" + gitCommit +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SubmitOptions that = (SubmitOptions) o;
        return Objects.equals(gitRepository, that.gitRepository) &&
                Objects.equals(gitBranch, that.gitBranch) &&
                Objects.equals(gitCommit, that.gitCommit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gitRepository, gitBranch, gitCommit);
    }
}
