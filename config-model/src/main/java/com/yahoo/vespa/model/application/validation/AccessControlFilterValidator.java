// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.component.chain.Chain;
import com.yahoo.vespa.model.container.http.AccessControl;
import com.yahoo.vespa.model.container.http.Filter;
import com.yahoo.vespa.model.container.http.FilterChains;
import com.yahoo.vespa.model.container.http.Http;

/**
 * Validates that 'access-control' is not enabled when no access control filter implementation is available.
 *
 * @author bjorncs
 */
public class AccessControlFilterValidator extends Validator {

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        model.getContainerClusters().forEach((id, cluster) -> {
            Http http = cluster.getHttp();
            if (http != null) {
                if (http.getAccessControl().isPresent()) {
                    verifyAccessControlFilterPresent(http);
                }
            }
        });
    }

    private static void verifyAccessControlFilterPresent(Http http) {
        FilterChains filterChains = http.getFilterChains();
        Chain<Filter> chain = filterChains.allChains().getComponent(AccessControl.ACCESS_CONTROL_CHAIN_ID);
        if (chain.getInnerComponents().isEmpty()) {
            // No access control filter configured - it's up to a config model plugin to provide an implementation of an access control filter.
            throw new IllegalArgumentException("The 'access-control' feature is not available in open-source Vespa.");
        }
    }
}
