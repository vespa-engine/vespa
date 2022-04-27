// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.misc;

import java.time.LocalDate;
import java.util.Objects;

/**
 * @author smorgrav
 */
public class User {

    public static final String ATTRIBUTE_NAME = "vespa.user.attributes";
    public static final LocalDate NO_DATE = LocalDate.EPOCH;

    private final String email;
    private final String name;
    private final String nickname;
    private final String picture;
    private final boolean isVerified;
    private final int loginCount;
    private final LocalDate lastLogin;

    public User(String email, String name, String nickname, String picture) {
        this.email = Objects.requireNonNull(email);
        this.name = name;
        this.nickname = nickname;
        this.picture = picture;
        this.isVerified = false;
        this.loginCount = -1;
        this.lastLogin = NO_DATE;
    }

    public User(String email, String name, String nickname, String picture, boolean isVerified, int loginCount, LocalDate lastLogin) {
        this.email = Objects.requireNonNull(email);
        this.name = name;
        this.nickname = nickname;
        this.picture = picture;
        this.isVerified = isVerified;
        this.loginCount = loginCount;
        this.lastLogin = Objects.requireNonNull(lastLogin);
    }

    public String name() {
        return name;
    }

    public String email() {
        return email;
    }

    public String nickname() {
        return nickname;
    }

    public String picture() {
        return picture;
    }

    public LocalDate lastLogin() { return lastLogin; }

    public boolean isVerified() { return isVerified; }

    public int loginCount() { return loginCount; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(name, user.name) &&
                Objects.equals(email, user.email) &&
                Objects.equals(nickname, user.nickname) &&
                Objects.equals(picture, user.picture) &&
                Objects.equals(lastLogin, user.lastLogin) &&
                loginCount == user.loginCount &&
                isVerified == user.isVerified;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, email, nickname, picture, lastLogin, loginCount, isVerified);
    }
}
