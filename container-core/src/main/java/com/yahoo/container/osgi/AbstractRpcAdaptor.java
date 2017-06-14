// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.osgi;

import com.yahoo.jrt.Supervisor;

/**
 * Helper class for optional RPC adaptors in the Container.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public abstract class AbstractRpcAdaptor {

    public abstract void bindCommands(Supervisor supervisor);

}
