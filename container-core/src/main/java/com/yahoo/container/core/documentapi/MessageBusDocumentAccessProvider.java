package com.yahoo.container.core.documentapi;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.messagebus.MessageBusDocumentAccess;
import com.yahoo.documentapi.messagebus.MessageBusParams;
import com.yahoo.documentapi.messagebus.loadtypes.LoadTypeSet;
import com.yahoo.vespa.config.content.LoadTypeConfig;

/**
 * Has a lazily populated reference to a {@link MessageBusDocumentAccess}.
 *
 * @author jonmv
 */
public class MessageBusDocumentAccessProvider extends AbstractComponent implements Provider<DocumentAccess> {

    private final DocumentmanagerConfig documentmanagerConfig;
    private final LoadTypeConfig loadTypeConfig;
    private final Object monitor = new Object();
    private boolean shutDown = false;
    private DocumentAccess access = null;

    @Inject
    public MessageBusDocumentAccessProvider(DocumentmanagerConfig documentmanagerConfig, LoadTypeConfig loadTypeConfig) {
        this.documentmanagerConfig = documentmanagerConfig;
        this.loadTypeConfig = loadTypeConfig;
    }

    @Override
    public DocumentAccess get() {
        synchronized (monitor) {
            if (access == null) {
                access = new MessageBusDocumentAccess((MessageBusParams) new MessageBusParams(new LoadTypeSet(loadTypeConfig)).setDocumentmanagerConfig(documentmanagerConfig));
                if (shutDown)
                    access.shutdown();
            }
            return access;
        }
    }

    @Override
    public void deconstruct() {
        synchronized (monitor) {
            if ( ! shutDown) {
                shutDown = true;
                if (access != null)
                    access.shutdown();
            }
        }
    }

}
