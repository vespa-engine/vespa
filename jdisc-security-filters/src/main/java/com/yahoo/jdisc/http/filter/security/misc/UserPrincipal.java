// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.misc;

import java.security.Principal;

/**
 * @author bjorncs
 */
public class UserPrincipal implements Principal {

    private final User user;

    public UserPrincipal(User user) {
        this.user = user;
    }

    @Override public String getName() { return user.name(); }

    public User user() { return user; }
}
