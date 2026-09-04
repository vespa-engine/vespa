// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.io.reader.NamedReader;

import java.util.List;

/**
 * Provides the platform-owned Vespa Commerce Discovery schemas. Consulted for every application
 * built in hosted Vespa when the {@code commerce-discovery} feature flag is enabled for the
 * application — whether the application uses the {@code <commerce-discovery>} services.xml
 * element is not checked by the caller. Provider implementations must detect it from the
 * application package. At most one provider may be registered.
 *
 * @author sebasabe
 */
public interface CommerceDiscoveryProvider {

    /**
     * Returns the schemas to add to this application, as named readers of schema source, on top
     * of the schemas in the application package. Implementations decide from the package what to
     * provide: an empty list for an application that does not use the
     * {@code <commerce-discovery>} element, and otherwise the schemas keyed on the element's
     * version, so element versions can carry different schema families.
     */
    List<NamedReader> schemas(ApplicationPackage applicationPackage);

    /**
     * Returns the content cluster document declarations to add for this application, on top of
     * what the application package declares. As with {@link #schemas}, implementations decide
     * from the package what to provide, and {@link AdditionalDocuments#none()} means
     * no addition. Every declared type must have its schema among {@link #schemas}; the content
     * cluster build fails otherwise.
     *
     * The declarations apply to every content cluster of the application, so a provider must
     * either require the application to have a single content cluster, or accept that each cluster
     * stores every declared type. Version 1.0 of {@code <commerce-discovery>} requires a single
     * content cluster; an element version supporting several must extend
     * {@link AdditionalDocuments.DocumentTypeDeclaration} with a cluster reference.
     */
    AdditionalDocuments documentDeclarations(ApplicationPackage applicationPackage);

}
