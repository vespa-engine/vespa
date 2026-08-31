// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.io.reader.NamedReader;

import java.util.List;

/**
 * Provides the platform-owned Vespa Commerce Discovery schemas for an application using the
 * {@code <commerce-discovery>} services.xml element. Currently only consulted when the
 * {@code commerce-discovery} feature flag is enabled.
 *
 * @author sebasabe
 */
public interface CommerceDiscoverySchemaProvider {

    /** The schemas to add to this application, as named readers of schema source. Implementations
     *  must key what they provide on the {@code <commerce-discovery>} element's version, so element
     *  versions can carry different schema families. */
    List<NamedReader> schemas(ApplicationPackage applicationPackage);

}
