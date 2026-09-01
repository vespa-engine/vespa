// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.io.reader.NamedReader;

import java.util.List;

/**
 * Provides the platform-owned Vespa Commerce Discovery schemas. Consulted for every application
 * built in hosted Vespa when the {@code commerce-discovery} feature flag is enabled for the
 * application — whether the application uses the {@code <commerce-discovery>} services.xml
 * element is for the implementation to decide. At most one provider may be registered.
 *
 * @author sebasabe
 */
public interface CommerceDiscoverySchemaProvider {

    /**
     * Returns the schemas to add to this application, as named readers of schema source, on top
     * of the schemas in the application package. Implementations decide from the package what to
     * provide: an empty list for an application that does not use the
     * {@code <commerce-discovery>} element, and otherwise the schemas keyed on the element's
     * version, so element versions can carry different schema families.
     */
    List<NamedReader> schemas(ApplicationPackage applicationPackage);

}
