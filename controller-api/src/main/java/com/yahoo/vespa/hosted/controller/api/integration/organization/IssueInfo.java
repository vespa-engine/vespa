package com.yahoo.vespa.hosted.controller.api.integration.organization;

import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;

import java.time.Instant;
import java.util.Optional;

/**
 * Information about a stored issue.
 *
 * @author jonmv
 */
public class IssueInfo {

    private final IssueId id;
    private final Instant updated;
    private final Status status;
    private final User assignee;

    public IssueInfo(IssueId id, Instant updated, Status status, User assignee) {
        this.id = id;
        this.updated = updated;
        this.status = status;
        this.assignee = assignee;
    }

    public IssueId id() {
        return id;
    }

    public Instant updated() {
        return updated;
    }

    public Status status() {
        return status;
    }

    public Optional<User> assignee() {
        return Optional.ofNullable(assignee);
    }


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
