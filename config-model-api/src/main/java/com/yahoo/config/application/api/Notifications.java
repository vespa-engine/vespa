// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyList;

/**
 * Configuration of notifications for deployment jobs.
 *
 * Supports a list of email addresses, and a list of roles for which email addresses are known.
 * The currently supported roles are:
 * <ul>
 *     <li><strong>author</strong>: the author of the git commit of a given application package. </li>
 * </ul>
 *
 * @author jonmv
 */
public class Notifications {

    private static final Notifications none = new Notifications(Collections.emptyMap(), Collections.emptyMap());
    public static Notifications none() { return none; }

    private final Map<When, List<String>> emailAddresses;
    private final Map<When, List<Role>> emailRoles;

    private Notifications(Map<When, List<String>> emailAddresses, Map<When, List<Role>> emailRoles) {
        this.emailAddresses = emailAddresses;
        this.emailRoles = emailRoles;
    }

    /**
     * Returns a new Notifications as specified by the given String input.
     *
     * @param emailAddressesByWhen What email addresses to notify, indexed by when to notify them.
     * @param emailRolesByWhen What roles to infer email addresses for, and notify, indexed by when to notify them.
     * @return The Notifications as specified.
     */
    public static Notifications of(Map<When, List<String>> emailAddressesByWhen, Map<When, List<Role>> emailRolesByWhen) {
        if (   emailAddressesByWhen.values().stream().allMatch(List::isEmpty)
            && emailRolesByWhen.values().stream().allMatch(List::isEmpty))
            return none;

        ImmutableMap.Builder<When, List<String>> emailAddresses = ImmutableMap.builder();
        emailAddressesByWhen.forEach((when, addresses) -> emailAddresses.put(when, ImmutableList.copyOf(addresses)));

        ImmutableMap.Builder<When, List<Role>> emailRoles = ImmutableMap.builder();
        emailRolesByWhen.forEach((when, roles) -> emailRoles.put(when, ImmutableList.copyOf(roles)));

        return new Notifications(emailAddresses.build(), emailRoles.build());
    }

    /** Returns all email addresses to notify for the given condition. */
    public Set<String> emailAddressesFor(When when) {
        ImmutableSet.Builder<String> addresses = ImmutableSet.builder();
        addresses.addAll(emailAddresses.getOrDefault(when, emptyList()));
        for (When include : when.includes)
            addresses.addAll(emailAddressesFor(include));
        return addresses.build();
    }

    /** Returns all roles for which email notification is to be sent for the given condition. */
    public Set<Role> emailRolesFor(When when) {
        ImmutableSet.Builder<Role> roles = ImmutableSet.builder();
        roles.addAll(emailRoles.getOrDefault(when, emptyList()));
        for (When include : when.includes)
            roles.addAll(emailRolesFor(include));
        return roles.build();
    }


    public enum Role {

        /** Author of the last commit of an application package. */
        author;

        public static String toValue(Role role) {
            switch (role) {
                case author: return "author";
                default: throw new IllegalArgumentException("Unexpected constant '" + role.name() + "'.");
            }
        }

        public static Role fromValue(String value) {
            switch (value) {
                case "author": return author;
                default: throw new IllegalArgumentException("Unknown value '" + value + "'.");
            }
        }

    }


    public enum When {

        /** Send notifications whenever a job fails. */
        failing,

        /** Send notifications whenever a job fails while deploying a new commit. */
        failingCommit(failing);

        private final List<When> includes;

        When(When... includes) {
            this.includes = Arrays.asList(includes);
        }

        public static String toValue(When when) {
            switch (when) {
                case failing: return "failing";
                case failingCommit: return "failing-commit";
                default: throw new IllegalArgumentException("Unexpected constant '" + when.name() + "'.");
            }
        }

        public static When fromValue(String value) {
            switch (value) {
                case "failing": return failing;
                case "failing-commit": return failingCommit;
                default: throw new IllegalArgumentException("Unknown value '" + value + "'.");
            }
        }

    }

}
