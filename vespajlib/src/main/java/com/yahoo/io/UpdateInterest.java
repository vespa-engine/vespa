// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import java.nio.channels.SelectionKey;


/**
 * Command object to perform interest set updates.  Workaround for NIO
 * design flaw which makes it impossible to update the interest set of
 * a SelectionKey while select() is in progress.  There should be a
 * more elegant way around this, but if it turns out to be performant
 * enough we leave it like this.
 *
 * <P>
 * Of course, the ideal would be to have NIO fixed.
 *
 * @author <a href="mailto:travisb@yahoo-inc.com">Bob Travis</a>
 * @author <a href="mailto:borud@yahoo-inc.com">Bjorn Borud</a>
 */
public class UpdateInterest {
    private SelectionKey key;
    private int operation;
    private boolean set;

    /**
     * Make sure this can't be run
     */
    @SuppressWarnings("unused")
    private UpdateInterest() {}

    /**
     * Create an object for encapsulating a interest set change
     * request.
     *
     * @param key The key we wish to update
     * @param operation The operation we wish to set or remove
     * @param set Whether we want to set (true) or clear (false) the
     *            operation in the interest set
     */
    public UpdateInterest(SelectionKey key, int operation, boolean set) {
        this.key = key;
        this.operation = operation;
        this.set = set;
    }

    /**
     * This method is used for actually applying the updates to the
     * SelectionKey in question at a time when it is safe to do so.
     * If the SelectionKey has been invalidated in the meanwhile we
     * do nothing.
     */
    public void doUpdate() {
        // bail if this key isn't valid anymore
        if ((key == null) || (!key.isValid())) {
            return;
        }

        if (set) {
            key.interestOps(key.interestOps() | operation);
        } else {
            key.interestOps(key.interestOps() & (~operation));
        }
    }
}
