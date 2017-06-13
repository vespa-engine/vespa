// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log.event;

/**
 *
 * @author  <a href="mailto:borud@yahoo-inc.com">Bjorn Borud</a>
 */
public class Crash extends Event {
    public Crash () {
    }

    public Crash (String name, int pid, int signal) {
        setValue("name", name);
        setValue("pid", Integer.toString(pid));
        setValue("signal", Integer.toString(signal));
    }
}
