package com.yahoo.container.core.documentapi;

import com.google.inject.Inject;
import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.documentapi.AsyncParameters;
import com.yahoo.documentapi.AsyncSession;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.DocumentAccessParams;
import com.yahoo.documentapi.SubscriptionParameters;
import com.yahoo.documentapi.SubscriptionSession;
import com.yahoo.documentapi.SyncParameters;
import com.yahoo.documentapi.SyncSession;
import com.yahoo.documentapi.VisitorDestinationParameters;
import com.yahoo.documentapi.VisitorDestinationSession;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.VisitorSession;
import com.yahoo.documentapi.messagebus.MessageBusDocumentAccess;
import com.yahoo.documentapi.messagebus.MessageBusParams;
import com.yahoo.documentapi.messagebus.loadtypes.LoadTypeSet;
import com.yahoo.messagebus.MessagebusConfig;
import com.yahoo.vespa.config.content.LoadTypeConfig;

/**
 * Lets a lazily initialised DocumentAccess forwarding to a real MessageBusDocumentAccess be injected in containers.
 *
 * @author jonmv
 */
public class DocumentAccessProvider extends AbstractComponent implements Provider<DocumentAccessProvider.LazyWrapper> {

    private final DocumentAccessProvider.LazyWrapper access;

    @Inject
    // TODO jonmv: Have Slobrok and RPC config injected as well.
    public DocumentAccessProvider(DocumentmanagerConfig documentmanagerConfig, LoadTypeConfig loadTypeConfig,
                                  SlobroksConfig slobroksConfig, MessagebusConfig messagebusConfig) {
        this.access = new LazyWrapper(documentmanagerConfig, loadTypeConfig, slobroksConfig, messagebusConfig);
    }

    @Override
    public DocumentAccessProvider.LazyWrapper get() {
        return access;
    }

    @Override
    public void deconstruct() {
        access.shutdown();
    }


    public static class LazyWrapper extends DocumentAccess {

        private final MessageBusParams parameters;
        private final Object monitor = new Object();

        private DocumentAccess delegate = null;
        private boolean shutDown = false;

        private LazyWrapper(DocumentmanagerConfig documentmanagerConfig,
                            LoadTypeConfig loadTypeConfig,
                            SlobroksConfig slobroksConfig,
                            MessagebusConfig messagebusConfig) {
            super(new DocumentAccessParams().setDocumentmanagerConfig(documentmanagerConfig));
            this.parameters = new MessageBusParams(new LoadTypeSet(loadTypeConfig));
            this.parameters.setDocumentmanagerConfig(documentmanagerConfig);
            this.parameters.getRPCNetworkParams().setSlobroksConfig(slobroksConfig);
            this.parameters.getMessageBusParams().setMessageBusConfig(messagebusConfig);
        }

        private DocumentAccess delegate() {
            synchronized (monitor) {
                if (delegate == null) {
                    if (shutDown)
                        throw new IllegalStateException("This document access has been shut down");

                    delegate = new MessageBusDocumentAccess(parameters);
                }
                return delegate;
            }
        }

        @Override
        public void shutdown() {
            synchronized (monitor) {
                super.shutdown();
                shutDown = true;
                if (delegate != null)
                    delegate.shutdown();
            }
        }

        @Override
        public SyncSession createSyncSession(SyncParameters parameters) {
            return delegate().createSyncSession(parameters);
        }

        @Override
        public AsyncSession createAsyncSession(AsyncParameters parameters) {
            return delegate().createAsyncSession(parameters);
        }

        @Override
        public VisitorSession createVisitorSession(VisitorParameters parameters) throws ParseException {
            return delegate().createVisitorSession(parameters);
        }

        @Override
        public VisitorDestinationSession createVisitorDestinationSession(VisitorDestinationParameters parameters) {
            return delegate().createVisitorDestinationSession(parameters);
        }

        @Override
        public SubscriptionSession createSubscription(SubscriptionParameters parameters) {
            return delegate().createSubscription(parameters);
        }

        @Override
        public SubscriptionSession openSubscription(SubscriptionParameters parameters) {
            return delegate().openSubscription(parameters);
        }

    }

}
