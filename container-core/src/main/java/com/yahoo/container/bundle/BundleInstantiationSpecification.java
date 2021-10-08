// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.bundle;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;


/**
 * Specifies how a component should be instantiated from a bundle.
 *
 * Immutable
 *
 * @author Tony Vaagenes
 */
public final class BundleInstantiationSpecification {

    public final ComponentId id;
    public final ComponentSpecification classId;
    public final ComponentSpecification bundle;

    public BundleInstantiationSpecification(ComponentSpecification id,  ComponentSpecification classId, ComponentSpecification bundle) {
        this.id = id.toId();
        this.classId = (classId != null) ? classId : id.withoutNamespace();
        this.bundle = (bundle != null) ? bundle : this.classId;
    }

    // Must only be used when classId != null, otherwise the id must be handled as a ComponentSpecification
    // (since converting a spec string to a ComponentId and then to a ComponentSpecification causes loss of information).
    public BundleInstantiationSpecification(ComponentId id, ComponentSpecification classId, ComponentSpecification bundle) {
        this(id.toSpecification(), classId, bundle);
        assert (classId!= null);
    }

    private static final String defaultInternalBundle = "container-search-and-docproc";

    private static BundleInstantiationSpecification getInternalSpecificationFromString(String idSpec, String classSpec) {
        return new BundleInstantiationSpecification(
                new ComponentSpecification(idSpec),
                (classSpec == null || classSpec.isEmpty())?  null  : new ComponentSpecification(classSpec),
                new ComponentSpecification(defaultInternalBundle));
    }

    public static BundleInstantiationSpecification getInternalSearcherSpecification(ComponentSpecification idSpec,
                                                                                    ComponentSpecification classSpec) {
        return new BundleInstantiationSpecification(idSpec, classSpec, new ComponentSpecification(defaultInternalBundle));
    }

    // TODO: These are the same for now because they are in the same bundle.
    public static BundleInstantiationSpecification getInternalHandlerSpecificationFromStrings(String idSpec, String classSpec) {
        return getInternalSpecificationFromString(idSpec, classSpec);
    }

    public static BundleInstantiationSpecification getInternalProcessingSpecificationFromStrings(String idSpec, String classSpec) {
        return getInternalSpecificationFromString(idSpec, classSpec);
    }

    public static BundleInstantiationSpecification getInternalSearcherSpecificationFromStrings(String idSpec, String classSpec) {
        return getInternalSpecificationFromString(idSpec, classSpec);
    }

    public static BundleInstantiationSpecification getFromStrings(String idSpec, String classSpec, String bundleSpec) {
        return new BundleInstantiationSpecification(
                new ComponentSpecification(idSpec),
                (classSpec == null || classSpec.isEmpty())?  null  : new ComponentSpecification(classSpec),
                (bundleSpec == null || bundleSpec.isEmpty())? null : new ComponentSpecification(bundleSpec));
    }

    /**
     * Return a new instance of the specification with bundle name altered
     *
     * @param bundleName the new name of the bundle
     * @return the new instance of the specification
     */
    public BundleInstantiationSpecification inBundle(String bundleName) {
        return new BundleInstantiationSpecification(this.id, this.classId, new ComponentSpecification(bundleName));
    }

    public String getClassName() {
        return classId.getName();
    }

    public BundleInstantiationSpecification nestInNamespace(ComponentId namespace) {
        return new BundleInstantiationSpecification(id.nestInNamespace(namespace), classId, bundle);
    }

}
