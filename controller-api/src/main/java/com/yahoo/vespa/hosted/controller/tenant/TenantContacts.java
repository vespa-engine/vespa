// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Tenant contacts are targets of the notification system.  Sometimes they
 * are a person with an email address, other times they are a Slack channel,
 * IRC plugin, etc.
 *
 * @author ogronnesby
 */
public class TenantContacts {
    private final List<Contact<?>> contacts;

    public TenantContacts(List<Contact<?>> contacts) {
        this.contacts = List.copyOf(contacts);
    }

    public static TenantContacts empty() {
        return new TenantContacts(List.of());
    }

    public List<Contact<?>> all() {
        return contacts;
    }

    public boolean isEmpty() {
        return contacts.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantContacts that = (TenantContacts) o;
        return contacts.equals(that.contacts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contacts);
    }

    @Override
    public String toString() {
        return "TenantContacts{" +
                "contacts=" + contacts +
                '}';
    }

    public static class Contact<T> {
        private final Type type;
        private final List<Audience> audiences;
        protected final T data;

        public Contact(Type type, List<Audience> audiences, T data) {
            this.type = type;
            this.audiences = audiences;
            this.data = data;
            if (audiences.isEmpty()) throw new IllegalArgumentException("audience cannot be empty");
        }

        public Type type() { return type; }
        public List<Audience> audiences() { return audiences; }
        public T data() { return data; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Contact<?> contact = (Contact<?>) o;
            return type == contact.type && audiences.equals(contact.audiences) && data.equals(contact.data);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, audiences, data);
        }

        @Override
        public String toString() {
            return "Contact{" +
                    "type=" + type +
                    ", audience=" + audiences +
                    ", data=" + data +
                    '}';
        }
    }

    public static class EmailContact {
        private final String email;

        public EmailContact(String email) {
            this.email = email;
        }

        public String email() { return email; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EmailContact that = (EmailContact) o;
            return email.equals(that.email);
        }

        @Override
        public int hashCode() {
            return Objects.hash(email);
        }

        @Override
        public String toString() {
            return "EmailContact{" +
                    "email='" + email + '\'' +
                    '}';
        }
    }

    public enum Type {
        EMAIL("email");

        private final String value;

        Type(String value) {
            this.value = value;
        }

        public String value() {
            return this.value;
        }

        public static Optional<Type> from(String value) {
            return Arrays.stream(Type.values()).filter(x -> x.value().equals(value)).findAny();
        }
    }

    public enum Audience {
        // tenant admin type updates about billing etc.
        TENANT("tenant"),

        // system notifications like deployment failures etc.
        NOTIFICATIONS("notifications");

        private final String value;

        Audience(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static Optional<Audience> from(String value) {
            return Arrays.stream(Audience.values()).filter((x -> x.value().equals(value))).findAny();
        }
    }
}
