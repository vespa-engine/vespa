// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import java.util.ArrayList;
import java.util.List;

/**
 * A RejectFilter can be queried to see if a log message should be rejected.
 *
 * @author Ulf Lilleengen
 * @since 5.1
 * Should only be used internally in the log library
 */
class RejectFilter {
    private final List<String> rejectedMessages = new ArrayList<>();

    public boolean shouldReject(String message) {
        if (message == null)
            return false;

        for (String rejectedMessage : rejectedMessages) {
            if (message.contains(rejectedMessage)) {
                return true;
            }
        }
        return false;
    }

    public void addRejectedMessage(String rejectedMessage) {
        rejectedMessages.add(rejectedMessage);
    }

    public static RejectFilter createDefaultRejectFilter() {
        RejectFilter reject = new RejectFilter();
        reject.addRejectedMessage("Using FILTER_NONE:  This must be paranoid approved, and since you are using FILTER_NONE you must live with this error.");
        return reject;
    }
}
