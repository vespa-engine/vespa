// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.component;// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import java.nio.file.FileSystem;
import java.util.EnumSet;
import java.util.logging.Logger;

public interface TaskContext {
    enum Cloud { YAHOO, AWS }
    Cloud cloud();

    enum Role { TENANT_DOCKER_HOST, CONFIG_SERVER_DOCKER_HOST }
    EnumSet<Role> roles();
    default boolean hasRole(Role role) {
        return roles().contains(role);
    }

    FileSystem fileSystem();

    void logSystemModification(Logger logger, String actionDescription);
}
