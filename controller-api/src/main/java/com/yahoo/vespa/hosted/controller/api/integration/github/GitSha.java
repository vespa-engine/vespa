// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.github;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

/**
 * @author mpolden
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitSha {

    @JsonProperty("sha")
    public final String sha;

    @JsonProperty("commit")
    public final GitCommit commit;

    @JsonCreator
    public GitSha(@JsonProperty("sha") String sha, @JsonProperty("commit") GitCommit commit) {
        this.sha = sha;
        this.commit = commit;
    }

    public static class GitCommit {
        @JsonProperty("author")
        public final GitAuthor author;

        @JsonCreator
        public GitCommit(@JsonProperty("author") GitAuthor author) {
            this.author = author;
        }
    }

    public static class GitAuthor {

        @JsonProperty("name")
        public final String name;
        @JsonProperty("email")
        public final String email;
        @JsonProperty("date")
        public final Date date;

        @JsonCreator
        public GitAuthor(@JsonProperty("name") String name, @JsonProperty("email") String email,
                         @JsonProperty("date") Date date) {
            this.name = name;
            this.email = email;
            this.date = date;
        }
    }

}
