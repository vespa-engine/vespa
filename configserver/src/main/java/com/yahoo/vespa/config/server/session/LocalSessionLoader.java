// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

/**
 * Interface of a component that is able to load a session given a session id.
 *
 * @author Ulf Lilleengen
 */
public interface LocalSessionLoader {

    LocalSession loadSession(long sessionId);

}
