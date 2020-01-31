// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import java.util.Objects;

/**
 * A revision in a source repository
 * 
 * @author bratseth
 */
public class SourceRevision {

    private final String repository;
    private final String branch;
    private final String commit;
    
    public SourceRevision(String repository, String branch, String commit) {
        this.repository = nonBlank(repository, "repository cannot be null or empty");
        this.branch = nonBlank(branch, "branch cannot be null or empty");
        this.commit = nonBlank(commit, "commit cannot be null");
    }
    
    public String repository() { return repository; }
    public String branch() { return branch; }
    public String commit() { return commit; }
    
    @Override
    public int hashCode() { return Objects.hash(repository, branch, commit); }
    
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof SourceRevision)) return false;

        SourceRevision other = (SourceRevision)o;
        return this.repository.equals(other.repository) && 
               this.branch.equals(other.branch) && 
               this.commit.equals(other.commit);
    }
    
    @Override
    public String toString() { return "source revision of repository '" + repository + 
                                      "', branch '" + branch + "' with commit '" + commit  + "'"; }


    private static String nonBlank(String s, String message) {
        Objects.requireNonNull(message);
        if (s.isBlank()) throw new IllegalArgumentException(message);
        return s;
    }

}
