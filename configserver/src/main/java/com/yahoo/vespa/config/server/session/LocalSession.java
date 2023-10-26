// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.provision.TenantName;

/**
 * A LocalSession is a session that has been created locally on this configserver. A local session can be edited and
 * prepared. Deleting a local session will ensure that the local filesystem state and global zookeeper state is
 * cleaned for this session.
 *
 * @author Ulf Lilleengen
 */
// This is really the store of an application, whether it is active or in an edit session
// TODO: Separate the "application store" and "session" aspects - the latter belongs in the HTTP layer   -bratseth
public class LocalSession extends Session {

    /**
     * Creates a session. This involves loading the application, validating it and distributing it.
     *
     * @param sessionId The session id for this session.
     */
    public LocalSession(TenantName tenant, long sessionId, ApplicationPackage applicationPackage,
                        SessionZooKeeperClient sessionZooKeeperClient) {
        super(tenant, sessionId, sessionZooKeeperClient, applicationPackage);
    }

}
