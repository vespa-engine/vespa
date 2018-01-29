// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.provider;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Map;

/**
 * Interface for supporting debug info to introspect e.g. internal state.
 *
 * @author hakon
 */
@ThreadSafe
public interface NodeAdminDebugHandler {
    /**
     * The Object in the map values must be serializable with Jackson's ObjectMapper.
     * May be called concurrently by different threads.
     */
    Map<String, Object> getDebugPage();
}
