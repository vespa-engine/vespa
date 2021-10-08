// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    public GitRepository repository;
    public GitBranch branch;
    public GitCommit commit;

    public static SubmitOptions from(String repository, String branch, String commit) {
        SubmitOptions options = new SubmitOptions();
        options.repository = new GitRepository(repository);
        options.branch = new GitBranch(branch);
        options.commit = new GitCommit(commit);
        return options;
    }

    @Override
    public String toString() {
        return "SubmitOptions{" +
                "repository=" + repository +
                ", branch=" + branch +
                ", commit=" + commit +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SubmitOptions that = (SubmitOptions) o;
        return Objects.equals(repository, that.repository) &&
                Objects.equals(branch, that.branch) &&
                Objects.equals(commit, that.commit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repository, branch, commit);
    }
}
