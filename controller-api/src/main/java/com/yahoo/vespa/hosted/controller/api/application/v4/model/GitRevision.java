// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitBranch;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitCommit;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitRepository;

import java.util.Objects;

/**
 * @author gv
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitRevision {

    public final GitRepository repository;
    public final GitBranch branch;
    public final GitCommit commit;

    @JsonCreator
    public GitRevision(@JsonProperty("repository") GitRepository repository,
                       @JsonProperty("branch") GitBranch branch,
                       @JsonProperty("commit") GitCommit commit) {
        this.repository = repository;
        this.branch = branch;
        this.commit = commit;
    }

    @Override
    public String toString() {
        return "GitRevision{" +
                "repository=" + repository +
                ", branch=" + branch +
                ", commit=" + commit +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GitRevision that = (GitRevision) o;
        return Objects.equals(repository, that.repository) &&
                Objects.equals(branch, that.branch) &&
                Objects.equals(commit, that.commit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repository, branch, commit);
    }
}
