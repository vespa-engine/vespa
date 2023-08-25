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
    private final AccountId assigneeId;

    public IssueInfo(IssueId id, Instant updated, Status status, AccountId assigneeId) {
        this.id = id;
        this.updated = updated;
        this.status = status;
        this.assigneeId = assigneeId;
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

    public Optional<AccountId> assigneeId() {
        return Optional.ofNullable(assigneeId);
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
