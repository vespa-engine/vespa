// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.jira;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author jvenstad
 */
// TODO: Make mock.
public class JiraMock implements Jira {

    public final Map<String, JiraCreateIssue.JiraFields> issues = new HashMap<>();

    private Long counter = 0L;

    @Override
    public List<JiraIssue> searchByProjectAndSummary(String project, String summary) {
        return issues.entrySet().stream()
                .filter(entry -> entry.getValue().project.key.equals(project))
                .filter(entry -> entry.getValue().summary.contains(summary))
                .map(entry -> new JiraIssue(entry.getKey(), new JiraIssue.Fields(Instant.now())))
                .collect(Collectors.toList());
    }

    @Override
    public JiraIssue createIssue(JiraCreateIssue issueData) {
        JiraIssue issue = uniqueKey();
        issues.put(issue.key, issueData.fields);
        return issue;
    }

    @Override
    public void commentIssue(JiraIssue issue, JiraComment comment) {
        // Add mock when relevant.
    }

    @Override
    public void addAttachment(JiraIssue issue, String filename, String fileContent) {
        // Add mock when relevant.
    }

    private JiraIssue uniqueKey() {
        return new JiraIssue((++counter).toString(), new JiraIssue.Fields(Instant.now()));
    }

}
