// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author mortent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraCreateIssue {

    @JsonProperty("fields")
    public final JiraFields fields;

    public JiraCreateIssue(JiraFields fields) {
        this.fields = fields;
    }

    public static class JiraFields {
        @JsonProperty("summary")
        public final String summary;

        @JsonProperty("description")
        public final String description;

        @JsonProperty("project")
        public final JiraProject project;

        @JsonProperty("issuetype")
        public final JiraIssueType issueType;

        @JsonProperty("components")
        public final List<JiraComponent> components;

        public JiraFields(
                JiraProject project,
                String summary,
                String description,
                JiraIssueType issueType,
                List<JiraComponent> components) {
            this.project = project;
            this.summary = summary;
            this.description = description;
            this.issueType = issueType;
            this.components = components;
        }


        public static class JiraProject {
            public static final JiraProject VESPA = new JiraProject("VESPA");

            @JsonProperty("key")
            public final String key;

            public JiraProject(String key) {
                this.key = key;
            }
        }

        public static class JiraIssueType {
            public static final JiraIssueType DEFECT = new JiraIssueType("Defect");

            @JsonProperty("name")
            public final String name;

            public JiraIssueType(String name) {
                this.name = name;
            }
        }

        public static class JiraComponent {
            public static final JiraComponent COREDUMPS = new JiraComponent("CoreDumps");

            @JsonProperty("name")
            public final String name;


            public JiraComponent(String name) {
                this.name = name;
            }
        }
    }
}
