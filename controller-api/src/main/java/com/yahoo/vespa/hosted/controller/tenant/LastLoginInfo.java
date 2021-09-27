// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author freva
 */
public class LastLoginInfo {

    public static final LastLoginInfo EMPTY = new LastLoginInfo(Map.of());

    private final Map<UserLevel, Instant> lastLoginByUserLevel;

    public LastLoginInfo(Map<UserLevel, Instant> lastLoginByUserLevel) {
        this.lastLoginByUserLevel = Map.copyOf(lastLoginByUserLevel);
    }

    public Optional<Instant> get(UserLevel userLevel) {
        return Optional.ofNullable(lastLoginByUserLevel.get(userLevel));
    }

    /**
     * Returns new instance with updated last login time if the given {@code loginAt} timestamp is after the current
     * for the given {@code userLevel}, otherwise returns this
     */
    public LastLoginInfo withLastLoginIfLater(UserLevel userLevel, Instant loginAt) {
        Instant lastLogin = lastLoginByUserLevel.getOrDefault(userLevel, Instant.EPOCH);
        if (loginAt.isAfter(lastLogin)) {
            Map<UserLevel, Instant> lastLoginByUserLevel = new HashMap<>(this.lastLoginByUserLevel);
            lastLoginByUserLevel.put(userLevel, loginAt);
            return new LastLoginInfo(lastLoginByUserLevel);
        }
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LastLoginInfo lastLoginInfo = (LastLoginInfo) o;
        return lastLoginByUserLevel.equals(lastLoginInfo.lastLoginByUserLevel);
    }

    @Override
    public int hashCode() {
        return lastLoginByUserLevel.hashCode();
    }

    public enum UserLevel { user, developer, administrator };
}
