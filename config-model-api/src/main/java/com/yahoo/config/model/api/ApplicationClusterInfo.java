// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.config.model.api;

import java.util.List;

public interface ApplicationClusterInfo {

    List<ApplicationClusterEndpoint> endpoints();

    boolean getDeferChangesUntilRestart();

    String name();

}
