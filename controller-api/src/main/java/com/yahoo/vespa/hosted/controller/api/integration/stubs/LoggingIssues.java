// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.vespa.hosted.controller.api.integration.Issues;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * An memory backed implementation of the Issues API which logs changes and does nothing else.
 * 
 * @author bratseth
 */
public class LoggingIssues implements Issues {

    private static final Logger log = Logger.getLogger(LoggingIssues.class.getName());
    
    /** Used to fabricate unique issue ids */
    private AtomicLong issueIdSequence = new AtomicLong(0);
    
    // These two maps should have precisely the same keys
    private final Map<String, Issue> issues = new HashMap<>();
    private final Map<String, IssueInfo> issueInfos = new HashMap<>();

    @Override
    public IssueInfo fetch(String issueId) {
        return issueInfos.getOrDefault(issueId, 
                                       new IssueInfo(issueId, null, Instant.ofEpochMilli(0), null, IssueInfo.Status.noCategory));
    }

    @Override
    public List<IssueInfo> fetchSimilarTo(Issue issue) {
        return Collections.emptyList();
    }

    @Override
    public String file(Issue issue) {
        log.info("Want to file " + issue);
        String issueId = "issue-" + issueIdSequence.getAndIncrement();
        issues.put(issueId, issue);
        issueInfos.put(issueId, new IssueInfo(issueId, null, Instant.now(), null, IssueInfo.Status.noCategory));
        return issueId;
    }

    @Override
    public void update(String issueId, String description) {
        log.info("Want to update " + issueId);
        issues.put(issueId, requireIssue(issueId).withDescription(description));
    }

    @Override
    public void reassign(String issueId, String assignee) {
        log.info("Want to reassign issue " + issueId + " to " + assignee);
        issueInfos.put(issueId, requireInfo(issueId).withAssignee(Optional.of(assignee)));
    }

    @Override
    public void addWatcher(String issueId, String watcher) {
        log.info("Want to add watcher " + watcher + " to issue " + issueId);
    }

    @Override
    public void comment(String issueId, String comment) {
        log.info("Want to comment on issue " + issueId);
    }
    
    private Issue requireIssue(String issueId) {
        Issue issue = issues.get(issueId);
        if (issue == null)
            throw new IllegalArgumentException("No issue with id '" + issueId + "'");
        return issue;
    }

    private IssueInfo requireInfo(String issueId) {
        IssueInfo info = issueInfos.get(issueId);
        if (info == null)
            throw new IllegalArgumentException("No issue info with id '" + issueId + "'");
        return info;
    }

}
