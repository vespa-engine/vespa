// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.component;

import java.util.logging.Logger;

public interface TaskContext {
    /**
     * Record a system modification. IdempotentTask is supposed to converge the system (files,
     * directory permission, iptable rules, etc) to some wanted state. It is especially important
     * to produce a truthful log of system changes to understand what may or may not be going on.
     *
     * All tasks should:
     *  1. Record any and all modifications to the system
     *  2. Avoid recording system interactions that does not actually change the system.
     *  3. Record system modifications as early as possible and preferably before they are
     *     performed (sometimes this is not possible).
     *
     * @param logger Used to log the modification to help locate the source of the modification.
     * @param description Description of the modification, e.g. "Changing owner of /foo from alice
     *                    to bob".
     */
    void recordSystemModification(Logger logger, String description);

    /**
     * Execute a task as a child of this task, and with its own sub-TaskContext. Please  avoid
     * excessive task hierarchies.
     */
    boolean executeSubtask(IdempotentTask task);
}
