package com.yahoo.config.application.api;

import com.google.common.collect.ImmutableMap;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    private final Map<String, When> staticEmails;
    private final Map<Role, When> roleEmails;

    private Notifications(Map<String, When> staticEmails, Map<Role, When> roleEmails) {
        this.staticEmails = ImmutableMap.copyOf(staticEmails);
        this.roleEmails = ImmutableMap.copyOf(roleEmails);
    }

    /**
     * Returns a new Notifications as specified by the given String input.
     *
     * @param when Optional string name of the default condition for sending notifications; defaults to failingCommit.
     * @param staticEmails Map from email addresses to optional overrides for when to send to these.
     * @param roleEmails Map from email roles to optional overrides for when to send to these.
     * @return The Notifications as specified.
     */
    public static Notifications of(Optional<String> when, Map<String, Optional<String>> staticEmails, Map<String, Optional<String>> roleEmails) {
        if (staticEmails.isEmpty() && roleEmails.isEmpty())
            return none;

        When defaultWhen = when.map(When::fromValue).orElse(When.failingCommit);
        return new Notifications(staticEmails.entrySet().stream()
                                             .collect(Collectors.toMap(entry -> entry.getKey(),
                                                                       entry -> entry.getValue().map(When::fromValue).orElse(defaultWhen))),
                                 roleEmails.entrySet().stream()
                                           .collect(Collectors.toMap(entry -> Role.fromValue(entry.getKey()),
                                                                     entry -> entry.getValue().map(When::fromValue).orElse(defaultWhen))));
    }

    public Map<String, When> staticEmails() {
        return staticEmails;
    }

    public Map<Role, When> roleEmails() {
        return roleEmails;
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
        failingCommit;

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
