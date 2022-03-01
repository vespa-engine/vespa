// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athenz;

/**
 * @author olaa
 *
 * Responsible for synchronizing misc roles and their pending memberships between separate Athenz instances
 */
public interface AthenzInstanceSynchronizer {

    void synchronizeInstances();

}
