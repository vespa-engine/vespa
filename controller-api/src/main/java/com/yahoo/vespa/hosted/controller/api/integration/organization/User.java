// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import java.util.Objects;

/**
 * Represents a human computer user, typically by UNIX account name.
 *
 * @author jonmv
 */
public class User {

    private final String username;

    protected User(String username) {
        this.username = username;
    }

    public String username() {
        return username;
    }

    public String displayName() {
        return username;
    }

    public static User from(String username) {
        if (username.isEmpty())
            throw new IllegalArgumentException("Username may not be empty!");

        return new User(username);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ( ! (o instanceof User)) return false;
        User that = (User) o;
        return Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

    @Override
    public String toString() {
        return username();
    }

}
