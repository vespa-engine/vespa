// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.component.ComponentId;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A set of sources with the same name, each associated with a different provider, that fills the same role.
 *
 * @author Tony Vaagenes
 */
final class SourceGroup {

    private final ComponentId id;
    private Source leader;
    private final Set<Source> participants = new LinkedHashSet<>();

    private void setLeader(Source leader) {
        assert (validMember(leader));

        if (this.leader != null)
            throw new IllegalArgumentException("There can not be two default providers for the source '" + id + "'");

        this.leader = leader;
    }

    private void addParticipant(Source source) {
        assert (validMember(source));
        assert (!source.equals(leader));

        if (!participants.add(source))
            throw new IllegalArgumentException("Source '" + source + "' added twice to the same group");
    }

    private boolean validMember(Source leader) {
        return leader.getComponentId().equals(id);
    }

    public ComponentId getComponentId() {
        return id;
    }

    public SourceGroup(ComponentId id) {
        this.id = id;
    }

    public void add(Source source) {
        if ( ! source.getComponentId().equals(getComponentId()))
            throw new IllegalStateException("Ids differ: " + source.getComponentId() + " and " + getComponentId());

        if (Source.GroupOption.leader == source.groupOption) {
            setLeader(source);
        } else {
            addParticipant(source);
        }
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Source id: ").append(id).append("\n").
                append("Leader provider: ").append(
                leader.getParentProvider().getComponentId()).append("\n").
                append("Participants:");

        for (Source participant : participants) {
            builder.append("\n").append("    Provider: ").append(
                    participant.getParentProvider().getComponentId());
        }
        return builder.toString();
    }

    public Source leader() {
        return leader;
    }

    public Collection<Source> participants() {
        return Collections.unmodifiableCollection(participants);
    }

    public void validate() {
        if (leader == null)
            throw new IllegalArgumentException("Missing leader for the source " + getComponentId() +
                                               ". One of the sources must use the attribute id instead of idref.");
    }

}
