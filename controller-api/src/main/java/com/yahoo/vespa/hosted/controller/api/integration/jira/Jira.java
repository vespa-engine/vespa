// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.jira;

import java.io.InputStream;
import java.util.List;

/**
 * @author mortent
 */
public interface Jira {

    List<JiraIssue> searchByProjectAndSummary(String project, String summary);

    JiraIssue createIssue(JiraCreateIssue issue);

    void commentIssue(JiraIssue issue, JiraComment comment);

    void addAttachment(JiraIssue issue, String filename, InputStream fileContent);
}
