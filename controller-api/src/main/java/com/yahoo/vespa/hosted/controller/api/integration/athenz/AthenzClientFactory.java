// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.vespa.athenz.api.NToken;

/**
 * @author bjorncs
 */
public interface AthenzClientFactory {

    ZmsClient createZmsClientWithServicePrincipal();

    ZtsClient createZtsClientWithServicePrincipal();

    ZmsClient createZmsClientWithAuthorizedServiceToken(NToken authorizedServiceToken);

}
