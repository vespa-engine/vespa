// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.client;

import com.yahoo.jdisc.application.Application;

/**
 * <p>This interface extends the {@link Application} interface, and is intended to be used with the {@link ClientDriver}
 * to implement stand-alone client applications on top of jDISC. The difference from Application is that this interface
 * provides a {@link Runnable#run()} method that will be invoked once the Application has been created and {@link
 * Application#start() started}. When run() returns, the {@link ClientDriver} will initiate Application shutdown.</p>
 *
 * @author Simon Thoresen Hult
 */
public interface ClientApplication extends Application, Runnable {

}
