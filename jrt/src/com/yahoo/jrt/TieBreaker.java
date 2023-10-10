// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


/**
 * When multiple actors race to come to a certain point and only one
 * should be allowed to continue, an object of this class may be used
 * to settle who should be allowed to continue. Note that external
 * synchronization is needed if this class is used with multiple
 * threads.
 **/
class TieBreaker {
    private boolean first = true;

    /**
     * Are we the first to come here?
     *
     * @return true if you are first, false otherwise.
     **/
    public boolean first() {
        boolean ret = first;
        first = false;
        return ret;
    }
}
