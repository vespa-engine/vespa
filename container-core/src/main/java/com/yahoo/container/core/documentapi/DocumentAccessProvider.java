// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.documentapi;

import com.google.inject.Inject;
import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.messagebus.MessagebusConfig;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocolPoliciesConfig;
import com.yahoo.vespa.config.content.DistributionConfig;
import com.yahoo.vespa.config.content.LoadTypeConfig;

/**
 * Lets a lazily initialised DocumentAccess forwarding to a real MessageBusDocumentAccess be injected in containers.
 *
 * @author jonmv
 */
public class DocumentAccessProvider extends AbstractComponent implements Provider<VespaDocumentAccess> {

    private final VespaDocumentAccess access;

    @Inject
    public DocumentAccessProvider(DocumentmanagerConfig documentmanagerConfig, LoadTypeConfig loadTypeConfig,
                                  SlobroksConfig slobroksConfig, MessagebusConfig messagebusConfig,
                                  DocumentProtocolPoliciesConfig policiesConfig, DistributionConfig distributionConfig) {
        this.access = new VespaDocumentAccess(documentmanagerConfig, loadTypeConfig, slobroksConfig, messagebusConfig,
                                              policiesConfig, distributionConfig);
    }

    @Override
    public VespaDocumentAccess get() {
        return access;
    }

    @Override
    public void deconstruct() {
        access.shutdown();
    }


}
