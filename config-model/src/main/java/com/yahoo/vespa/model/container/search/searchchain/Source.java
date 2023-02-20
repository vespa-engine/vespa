// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.search.searchchain.model.federation.FederationOptions;
import com.yahoo.config.model.producer.TreeConfigProducer;

import java.util.Arrays;


/**
 * Config producer for source, which is contained in a provider.
 *
 * @author Tony Vaagenes
 */
public class Source extends GenericTarget {

    //Each source group must have exactly one leader, and an arbitrary number of participants
    public enum GroupOption {
        leader,
        participant
    }

    public final GroupOption groupOption;

    public Source(ChainSpecification specWithoutInnerSearchers, FederationOptions federationOptions,
                  GroupOption groupOption) {
        super(specWithoutInnerSearchers, federationOptions);
        this.groupOption = groupOption;
    }

    @Override
    public FederationOptions federationOptions() {
        return super.federationOptions().inherit(getParentProvider().federationOptions());
    }

    @Override
    protected boolean useByDefault() {
        return false;
    }

    public Provider getParentProvider() {
        var parent = getParent();
        while (!(parent instanceof Provider)) {
            parent = parent.getParent();
        }
        return (Provider)parent;
    }

    @Override
    public ChainSpecification getChainSpecification() {
        return super.getChainSpecification().addInherits(
                Arrays.asList(getParentProvider().getComponentId().toSpecification()));
    }

    public ComponentId getGlobalComponentId() {
        return getComponentId().nestInNamespace(
                getParentProvider().getComponentId());
    }
}
