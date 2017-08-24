// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.Collection;
import java.util.Objects;

import static com.yahoo.vespa.hosted.controller.api.integration.Contacts.Category.unknown;
import static java.util.Comparator.reverseOrder;

/**
 * @author jvenstad
 */
public interface Contacts {

    /**
     * Returns the most relevant user of lowest non-empty level above that of @assignee, or, if no such user exists,
     * the @assignee with @Category information.
     */
    static UserContact escalationTargetFrom(Collection<UserContact> userContacts, String assignee) {
        return userContacts.stream()
                .filter(contact -> ! contact.username().isEmpty()) // don't assign to empty names
                .sorted(reverseOrder()).distinct() // Pick out the highest category per user.
                // Keep the assignee, or the last user on the first non-empty level above her.
                .sorted().reduce(new UserContact(assignee, assignee, unknown), (current, next) ->
                        next.is(assignee) || (current.is(assignee) ^ current.level() == next.level()) ? next : current);
    }

    /**
     * Return a list of all contact entries for property with id @propertyId, where username is set.
     */
    Collection<UserContact> userContactsFor(long propertyId);

    /** Returns the URL listing contacts for the given property */
    URI contactsUri(long propertyId);

    /**
     * Return a target of escalation above @assignee, from the set of @UserContact entries found for @propertyId.
     */
    default UserContact escalationTargetFor(long propertyId, String assignee) {
        return escalationTargetFrom(userContactsFor(propertyId), assignee);
    }

    /**
     * A list of contact roles, in the order in which we look for escalation targets.
     * Categories must be listed in increasing order of relevancy per level, and by increasing level.
     */
    enum Category {

        unknown(-1, Level.none, "Unknown"),
        admin(54, Level.grunt, "Administrator"), // TODO: Find more grunts?
        businessOwner(567, Level.owner, "Business Owner"),
        serviceOwner(646, Level.owner, "Service Engineering Owner"),
        engineeringOwner(566, Level.owner, "Engineering Owner"),
        vpBusiness(11, Level.VP, "VP Business"),
        vpService(647, Level.VP, "VP Service Engineering"),
        vpEngineering(9, Level.VP, "VP Engineering");

        public final long id;
        public final Level level;
        public final String name;

        Category(long id, Level level, String name) {
            this.id = id;
            this.level = level;
            this.name = name;
        }

        /** Find the category for the given id, or unknown if the id is unknown. */
        public static Category of(Long id) {
            for (Category category : values())
                if (category.id == id)
                    return category;
            return unknown;
        }

        public enum Level {
            none,
            grunt,
            owner,
            VP;
        }

    }

    /** Container class for user contact information; sorts by category and identifies by username. Immutable. */
    class UserContact implements Comparable<UserContact> {

        private final String username;
        private final String name;
        private final Category category;

        public UserContact(String username, String name, Category category) {
            Objects.requireNonNull(username, "username cannot be null");
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(category, "category cannot be null");
            this.username = username;
            this.name = name;
            this.category = category;
        }

        public String username() { return username; }
        public String name() { return name; }
        public Category category() { return category; }
        public Category.Level level() { return category.level; }

        public boolean is(String username) { return this.username.equals(username); }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UserContact that = (UserContact) o;
            return Objects.equals(username, that.username);
        }

        @Override
        public int hashCode() {
            return Objects.hash(username);
        }

        @Override
        public int compareTo(@NotNull UserContact other) {
            return category().compareTo(other.category());
        }

        @Override
        public String toString() {
            return String.format("%s, %s, %s", username, name, category.name);
        }

    }

}
