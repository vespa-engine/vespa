// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * @author jvenstad
 */
public interface Issues {

    /**
     * Returns information about an issue.
     * If this issue does not exist this returns an issue id containing the id and default values.
     */
    IssueInfo fetch(String issueId);

    /**
     * Returns the @Meta of all unresolved issues which have the same summary (and queue, if present) as @issue.
     */
    List<IssueInfo> fetchSimilarTo(Issue issue);

    /**
     * Files the given issue
     * 
     * @return the id of the created issue
     */
    String file(Issue issue);

    /**
     * Update the description fields of the issue stored with id @issueId to be @description.
     */
    void update(String issueId, String description);

    /**
     * Set the assignee of the issue with id @issueId to the user with usename @assignee.
     */
    void reassign(String issueId, String assignee);

    /**
     * Add the user with username @watcher to the watcher list of the issue with id @issueId.
     */
    void addWatcher(String issueId, String watcher);

    /**
     * Post @comment as a comment to the issue with id @issueId.
     */
    void comment(String issueId, String comment);


    /** Contains information used to file an issue with the responsible party; only @queue is mandatory. */
    class Classification {

        private final String queue;
        private final String component;
        private final String label;

        public Classification(String queue, String component, String label) {
            if (queue.isEmpty()) throw new IllegalArgumentException("Queue can not be empty!");

            this.queue = queue;
            this.component = component;
            this.label = label;
        }

        public Classification(String queue) {
            this(queue, null, null);
        }

        public Classification withComponent(String component) { return new Classification(queue, component, label); }
        public Classification withLabel(String label) { return new Classification(queue, component, label); }

        public String queue() { return queue; }
        public Optional<String> component() { return Optional.ofNullable(component); }
        public Optional<String> label() { return Optional.ofNullable(label); }

        @Override
        public String toString() {
            return
                    "Queue     :  " + queue()     + "\n" +
                    "Component :  " + component() + "\n" +
                    "Label     :  " + label()     + "\n";
        }

    }


    /** Information about a stored issue */
    class IssueInfo {

        private final String id;
        private final String key;
        private final Instant updated;
        private final Optional<String> assignee;
        private final Status status;

        public IssueInfo(String id, String key, Instant updated, Optional<String> assignee, Status status) {
            if (assignee == null || assignee.isPresent() && assignee.get().isEmpty()) // TODO: Throw on these things
                assignee = Optional.empty();
            this.id = id;
            this.key = key;
            this.updated = updated;
            this.assignee = assignee;
            this.status = status;
        }
        
        public IssueInfo withAssignee(Optional<String> assignee) { 
            return new IssueInfo(id, key, updated, assignee, status); 
        }

        public String id() { return id; }
        public String key() { return key; }
        public Instant updated() { return updated; }
        public Optional<String> assignee() { return assignee; }
        public Status status() { return status; }

        public enum Status {

            toDo("To Do"),
            inProgress("In Progress"),
            done("Done"),
            noCategory("No Category");

            private final String value;

            Status(String value) { this.value = value; }

            public static Status fromValue(String value) {
                for (Status status : Status.values())
                    if (status.value.equals(value))
                        return status;
                throw new IllegalArgumentException(value + " is not a valid status.");
            }

        }

    }


    /**
     * A representation of an issue with a Vespa application which can be reported and escalated through an external issue service.
     * This class is immutable.
     *
     * @author jvenstad
     */
    class Issue {

        private final String summary;
        private final String description;
        private final Classification classification;

        public Issue(String summary, String description, Classification classification) {
            if (summary.isEmpty()) throw new IllegalArgumentException("Summary can not be empty.");
            if (description.isEmpty()) throw new IllegalArgumentException("Description can not be empty.");

            this.summary = summary;
            this.description = description;
            this.classification = classification;
        }

        public Issue(String summary, String description) {
            this(summary, description, null);
        }

        public Issue with(Classification classification) {
            return new Issue(summary, description, classification);
        }
        public Issue withDescription(String description) { return new Issue(summary, description, classification); }

        /** Return a new @Issue with the description of @this, but with @appendage appended. */
        public Issue append(String appendage) {
            return new Issue(summary, description + "\n\n" + appendage, classification);
        }

        public String summary() { return summary; }
        public String description() { return description; }
        public Optional<Classification> classification() { return Optional.ofNullable(classification); }

    }

}
