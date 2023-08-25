// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Represents an issue which needs to reported, typically from the controller, to a responsible party,
 * the identity of which is determined by the propertyId and, possibly, assignee fields.
 *
 * @author jonmv
 */
public class Issue {

    private final String summary;
    private final String description;
    private final List<String> labels;
    private final User assignee;
    private final AccountId assigneeId;
    private final Type type;
    private final String queue;
    private final Optional<String> component;

    private Issue(String summary, String description, List<String> labels, User assignee,
                  AccountId assigneeId, Type type, String queue, Optional<String> component) {
        if (summary.isEmpty()) throw new IllegalArgumentException("Issue summary can not be empty!");
        if (description.isEmpty()) throw new IllegalArgumentException("Issue description can not be empty!");

        this.summary = summary;
        this.description = description;
        this.labels = List.copyOf(labels);
        this.assignee = assignee;
        this.assigneeId = assigneeId;
        this.type = type;
        this.queue = queue;
        this.component = component;
    }

    public Issue(String summary, String description, String queue, Optional<String> component) {
        this(summary, description, Collections.emptyList(), null,  null, Type.defect, queue, component);
    }

    public Issue append(String appendage) {
        return new Issue(summary, description + appendage, labels, assignee, assigneeId, type, queue, component);
    }

    public Issue with(String label) {
        List<String> labels = new ArrayList<>(this.labels);
        labels.add(label);
        return new Issue(summary, description, labels, assignee, assigneeId, type, queue, component);
    }

    public Issue with(List<String> labels) {
        List<String> newLabels = new ArrayList<>(this.labels);
        newLabels.addAll(labels);
        return new Issue(summary, description, newLabels, assignee, assigneeId, type, queue, component);
    }

    public Issue with(AccountId assigneeId) {
        return new Issue(summary, description, labels, null, assigneeId, type, queue, component);
    }

    public Issue with(User assignee) {
        return new Issue(summary, description, labels, assignee, null, type, queue, component);
    }

    public Issue with(Type type) {
        return new Issue(summary, description, labels, assignee, assigneeId, type, queue, component);
    }

    public Issue in(String queue) {
        return new Issue(summary, description, labels, assignee, assigneeId, type, queue, Optional.empty());
    }

    public Issue withoutComponent() {
        return new Issue(summary, description, labels, assignee, assigneeId, type, queue, Optional.empty());
    }

    public String summary() {
        return summary;
    }

    public String description() {
        return description;
    }

    public List<String> labels() {
        return labels;
    }

    public Optional<User> assignee() { return Optional.ofNullable(assignee);
    }

    public Optional<AccountId> assigneeId() {
        return Optional.ofNullable(assigneeId);
    }

    public Type type() {
        return type;
    }

    public String queue() {
        return queue;
    }

    public Optional<String> component() {
        return component;
    }

    public enum Type {

        defect, // A defect which needs fixing.
        task,   // A task the humans must perform.
        operationalTask // SRE and operational tasks.

    }

}
