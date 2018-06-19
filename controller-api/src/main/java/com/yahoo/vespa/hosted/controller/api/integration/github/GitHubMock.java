// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.github;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author jvenstad
 */
public class GitHubMock implements GitHub {

    private final Map<String, GitSha> tags = new HashMap<>();
    private boolean mockAny = true;

    @Override
    public GitSha getCommit(String owner, String repo, String ref) {
        if (mockAny) {
            String sha = UUID.randomUUID().toString();
            return new GitSha(sha, new GitSha.GitCommit(new GitSha.GitAuthor("foo", "foo@foo.tld",
                                                                             Date.from(Instant.EPOCH))));
        }
        if (tags.containsKey(ref)) {
            return tags.get(ref);
        }
        throw new IllegalArgumentException("Unknown ref: " + ref);
    }

    public GitHubMock knownTag(String tag, String sha) {
        this.tags.put(tag, new GitSha(sha, new GitSha.GitCommit(
                new GitSha.GitAuthor("foo", "foo@foo.tld", Date.from(Instant.EPOCH)))));
        return this;
    }

    public GitHubMock mockAny(boolean mockAny) {
        this.mockAny = mockAny;
        return this;
    }

    public GitHubMock reset() {
        tags.clear();
        mockAny = true;
        return this;
    }

}
