// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.jrt;

import com.yahoo.jrt.Supervisor;

/**
 * A factory for JRT {@link Supervisor}
 *
 * @author bjorncs
 */
public interface JrtFactory {

    Supervisor createSupervisor();

}
