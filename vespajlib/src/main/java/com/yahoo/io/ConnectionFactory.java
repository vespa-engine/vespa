// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;


/**
 * @author <a href="mailto:borud@yahoo-inc.com">Bj\u00F8rn Borud</a>
 */

import java.nio.channels.SocketChannel;


/**
 * A factory interface used for associating SocketChannel and Listener
 * information with the application's Connection object.
 *
 * @author <a href="mailto:borud@yahoo-inc.com">Bjorn Borud</a>
 * @author <a href="mailto:travisb@yahoo-inc.com">Bob Travis</a>
 */
public interface ConnectionFactory {
    public Connection newConnection(SocketChannel channel, Listener listener);
}
