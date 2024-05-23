// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.misc;

import java.time.LocalDate;
import java.util.Objects;

/**
 * @author smorgrav
 */
public record User(String email, String name, String nickname, String picture, boolean isVerified, int loginCount, LocalDate lastLogin) {
    public static final String ATTRIBUTE_NAME = "vespa.user.attributes";
    public static final LocalDate NO_DATE = LocalDate.EPOCH;

    public User {
        Objects.requireNonNull(email);
        Objects.requireNonNull(lastLogin);
    }

    public User(String email, String name, String nickname, String picture) {
        this(email, name, nickname, picture, false, -1, NO_DATE);
    }
}
