// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import java.util.Objects;

/**
 * @author olaa
 */
public class Email {

    private final String emailAddress;
    private final boolean isVerified;

    public Email(String emailAddress, boolean isVerified) {
        this.emailAddress = emailAddress;
        this.isVerified = isVerified;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public static Email empty() {
        return new Email("", true);
    }

    public Email withEmailAddress(String emailAddress) {
        return new Email(emailAddress, isVerified);
    }

    public Email withVerification(boolean isVerified) {
        return new Email(emailAddress, isVerified);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Email email = (Email) o;
        return isVerified == email.isVerified && Objects.equals(emailAddress, email.emailAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(emailAddress, isVerified);
    }

    @Override
    public String toString() {
        return emailAddress;
    }
}
