// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.client.zms.ZmsClient;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;

/**
 * @author bjorncs
 */
public interface AthenzClientFactory {

    AthenzIdentity getControllerIdentity();

    ZmsClient createZmsClient();

    ZtsClient createZtsClient();

    default boolean cacheLookups() { return false; }

}
