// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.user;

import com.yahoo.vespa.hosted.controller.api.role.SecurityContext;

/**
 * @author freva
 */
public interface UserSessionManager {

    /** Returns whether the existing session for the given SecurityContext should be expired */
    boolean shouldExpireSessionFor(SecurityContext context);
}
