// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.component;

import java.util.logging.Level;
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
     * @param message Description of the modification, e.g. "Changing owner of /foo from alice
     *                to bob".
     */
    void recordSystemModification(Logger logger, String message);
    default void recordSystemModification(Logger logger, String messageFormat, Object... args) {
        recordSystemModification(logger, String.format(messageFormat, args));
    }

    /**
     * Log message at Level.INFO, scoped to denote the current task. The message may
     * also be directed to status pages or similar.
     *
     * Please do not call this too many times as that spams the log. Typically a task may call
     * this zero times, or up to a few times.
     *
     * Do not log a message that is also recorded with recordSystemModification.
     */
    default void log(Logger logger, String message) {
        log(logger, Level.INFO, message);
    }

    default void log(Logger logger, String messageFormat, Object... args) {
        log(logger, String.format(messageFormat, args));
    }

    void log(Logger logger, Level level, String message);

    void log(Logger logger, Level level, String message, Throwable throwable);
}
