// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain.model.federation;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.yahoo.container.bundle.BundleInstantiationSpecification;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.search.federation.FederationSearcher;

/**
 * Specifies how a federation searcher is to be set up.
 *
 * @author Tony Vaagenes
 */
public class FederationSearcherModel extends ChainedComponentModel {

    private static final ComponentSpecification federationSearcherComponentSpecification =
            new ComponentSpecification(FederationSearcher.class.getName());

    public final List<TargetSpec> targets;
    public final boolean inheritDefaultSources;

    public FederationSearcherModel(ComponentSpecification componentId,
                                   Dependencies dependencies,
                                   List<TargetSpec> targets,
                                   boolean inheritDefaultSources) {
        super(BundleInstantiationSpecification.fromSearchAndDocproc(componentId, federationSearcherComponentSpecification),
              dependencies);
        this.inheritDefaultSources = inheritDefaultSources;
        this.targets = ImmutableList.copyOf(targets);
    }

    /** Specifies one or more search chains that can be addressed as a single source. */
    public static class TargetSpec {

        public final ComponentSpecification sourceSpec;
        public final FederationOptions federationOptions;

        public TargetSpec(ComponentSpecification sourceSpec, FederationOptions federationOptions) {
            this.sourceSpec = sourceSpec;
            this.federationOptions = federationOptions;
        }
    }

}
