// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.commerce.discovery;

import com.yahoo.vespa.model.productdiscovery.ProductDiscoveryProvider;

/**
 * The earlier name of {@link ProductDiscoveryProvider}, kept so providers implementing it are still consulted.
 * Implement {@link ProductDiscoveryProvider} instead.
 *
 * @author sebasabe
 */
// TODO (@sebasabe): Remove when provider implements ProductDiscoveryProvider instead
public interface CommerceDiscoveryProvider extends ProductDiscoveryProvider {

}
