// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;


/**
 * This interface defines a callback hook which applications can
 * use to get work done before or after the select loop finishes
 * its tasks.
 *
 * @author <a href="mailto:borud@yahoo-inc.com">Bjorn Borud</a>
 *
 */
public interface SelectLoopHook {

    /**
     * Callback which can be called before or after
     * select loop has done its work, depending on
     * how you register the hook.
     *
     * @param before is <code>true</code> if the hook
     *        was called before the channels in the ready
     *        set have been processed, and <code>false</code>
     *        if called after.
     */
    public void selectLoopHook(boolean before);
}
