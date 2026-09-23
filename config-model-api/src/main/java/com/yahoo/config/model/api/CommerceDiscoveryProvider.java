// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.config.application.api.ApplicationPackage;

/**
 * Provides the platform-owned Vespa Commerce Discovery content. Consulted for every application
 * built in hosted Vespa when the {@code commerce-discovery} feature flag is enabled for the
 * application — whether the application uses the {@code <commerce-discovery>} services.xml
 * element is not checked by the caller. Provider implementations must detect it from the
 * application package. At most one provider may be registered.
 *
 * @author sebasabe
 */
public interface CommerceDiscoveryProvider {

    /**
     * Returns the content to add to this application on top of its package: schemas, and the
     * content cluster document declarations of those schemas. Implementations decide from the
     * package what to provide: {@link AdditionalContent#none()} for an application that does not
     * use the {@code <commerce-discovery>} element, and otherwise the content keyed on the
     * element's version, so element versions can carry different schema families. Schemas and
     * declarations come in one result because they must match, which
     * {@link AdditionalContent} checks.
     *
     * The declarations apply to every content cluster of the application, so a provider must
     * either require the application to have a single content cluster, or accept that each cluster
     * stores every declared type. Version 1.0 of {@code <commerce-discovery>} requires a single
     * content cluster; an element version supporting several must extend
     * {@link AdditionalContent.DocumentTypeDeclaration} with a cluster reference.
     */
    AdditionalContent additionalContent(ApplicationPackage applicationPackage);

}
