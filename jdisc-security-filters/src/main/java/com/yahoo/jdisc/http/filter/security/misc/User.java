// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.misc;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * @author smorgrav
 */
public record User(String email, String name, String nickname, String picture, boolean isVerified, int loginCount,
                   LocalDate lastLogin, Map<String, Object> extraAttributes) {
    public static final String ATTRIBUTE_NAME = "vespa.user.attributes";
    public static final LocalDate NO_DATE = LocalDate.EPOCH;

    public User {
        Objects.requireNonNull(email);
        Objects.requireNonNull(lastLogin);
        extraAttributes = Map.copyOf(Objects.requireNonNull(extraAttributes));
    }

    public User(String email, String name, String nickname, String picture, boolean isVerified, int loginCount,
                LocalDate lastLogin) {
        this(email, name, nickname, picture, isVerified, loginCount, lastLogin, Map.of());
    }

    public User(String email, String name, String nickname, String picture) {
        this(email, name, nickname, picture, false, -1, NO_DATE, Map.of());
    }

    private User(Builder builder) {
        this(builder.email, builder.name, builder.nickname, builder.picture, builder.isVerified, builder.loginCount,
             Objects.requireNonNullElse(builder.lastLogin, NO_DATE), builder.extraAttributes);
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String email;
        private String name;
        private String nickname;
        private String picture;
        private boolean isVerified;
        private int loginCount;
        private LocalDate lastLogin;
        private final Map<String, Object> extraAttributes = new TreeMap<>();

        private Builder() {}

        public Builder email(String email) { this.email = email; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder nickname(String nickname) { this.nickname = nickname; return this; }
        public Builder picture(String picture) { this.picture = picture; return this; }
        public Builder isVerified(boolean isVerified) { this.isVerified = isVerified; return this; }
        public Builder loginCount(int loginCount) { this.loginCount = loginCount; return this; }
        public Builder lastLogin(LocalDate lastLogin) { this.lastLogin = lastLogin; return this; }
        public Builder extraAttribute(String key, Object value) {
            extraAttributes.put(Objects.requireNonNull(key), Objects.requireNonNull(value)); return this;
        }
        public User build() { return new User(this); }
    }
}
