// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.select.DocumentSelector;
import com.yahoo.document.select.Result;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.metrics.MetricSet;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RoutingContext;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * This policy is responsible for selecting among the given recipient routes according to the configured document
 * selection properties. To facilitate this the "routing" plugin in the vespa model builds a mapping from the route
 * names to a document selector and a feed name of every search cluster. This can very well be extended to include
 * storage at a later time.
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class DocumentRouteSelectorPolicy
        implements DocumentProtocolRoutingPolicy, ConfigSubscriber.SingleSubscriber<DocumentrouteselectorpolicyConfig> {

    private static Logger log = Logger.getLogger(DocumentRouteSelectorPolicy.class.getName());
    private Map<String, DocumentSelector> config;
    private String error = "Not configured.";
    private ConfigSubscriber subscriber;

    /**
     * This policy is constructed with a configuration identifier that can be subscribed to for the document selector
     * config. If the string is either null or empty it will default to the proper one.
     *
     * @param configId The configuration identifier to subscribe to.
     */
    public DocumentRouteSelectorPolicy(String configId) {
        subscriber = new ConfigSubscriber();
        subscriber.subscribe(this, DocumentrouteselectorpolicyConfig.class, configId);
    }

    /**
     * This is a safety mechanism to allow the constructor to fail and signal that it can not be used.
     *
     * @return The error string, or null if no error.
     */
    public synchronized String getError() {
        return error;
    }

    /**
     * This method is called when configuration arrives from the config server. The received config object is traversed
     * and a local map is constructed and swapped with the current {@link #config} map.
     *
     * @param cfg The configuration object given by subscription.
     */
    @Override
    public void configure(DocumentrouteselectorpolicyConfig cfg) {
        String error = null;
        Map<String, DocumentSelector> config = new HashMap<>();
        for (int i = 0; i < cfg.route().size(); i++) {
            DocumentrouteselectorpolicyConfig.Route route = cfg.route(i);
            if (route.selector().isEmpty()) {
                continue;
            }
            DocumentSelector selector;
            try {
                selector = new DocumentSelector(route.selector());
                log.log(LogLevel.CONFIG, "Selector for route '" + route.name() + "' is '" + selector + "'.");
            } catch (com.yahoo.document.select.parser.ParseException e) {
                error = "Error parsing selector '" + route.selector() + "' for route '" + route.name() + "; " +
                        e.getMessage();
                break;
            }
            config.put(route.name(), selector);
        }
        synchronized (this) {
            this.config = config;
            this.error = error;
        }
    }

    @Override
    public void select(RoutingContext context) {
        // Require that recipients have been configured.
        if (context.getNumRecipients() == 0) {
            context.setError(DocumentProtocol.ERROR_POLICY_FAILURE,
                             "No recipients configured.");
            return;
        }

        // Invoke private select method for each candidate recipient.
        synchronized (this) {
            if (error != null) {
                context.setError(DocumentProtocol.ERROR_POLICY_FAILURE, error);
                return;
            }
            for (int i = 0; i < context.getNumRecipients(); ++i) {
                Route recipient = context.getRecipient(i);
                String routeName = recipient.toString();
                if (select(context, routeName)) {
                    Route route = context.getMessageBus().getRoutingTable(DocumentProtocol.NAME).getRoute(routeName);
                    context.addChild(route != null ? route : recipient);
                }
            }
        }
        context.setSelectOnRetry(false);

        // Notify that no children were selected, this is to differentiate this from the NO_RECIPIENTS_FOR_ROUTE error
        // that message bus will generate if there are no recipients and no reply.
        if (context.getNumChildren() == 0) {
            context.setReply(new DocumentIgnoredReply());
        }
    }

    /**
     * This method runs the selector associated with the given location on the content of the message. If the selector
     * validates the location, this method returns true.
     *
     * @param context   The routing context that contains the necessary data.
     * @param routeName The candidate route whose selector to run.
     * @return Whether or not to send to the given recipient.
     */
    private boolean select(RoutingContext context, String routeName) {
        if (config == null) {
            return true;
        }
        DocumentSelector selector = config.get(routeName);
        if (selector == null) {
            return true;
        }

        // Select based on message content.
        Message msg = context.getMessage();
        switch (msg.getType()) {

        case DocumentProtocol.MESSAGE_PUTDOCUMENT:
            return selector.accepts(((PutDocumentMessage)msg).getDocumentPut()) == Result.TRUE;

        case DocumentProtocol.MESSAGE_UPDATEDOCUMENT:
            return selector.accepts(((UpdateDocumentMessage)msg).getDocumentUpdate()) != Result.FALSE;

        case DocumentProtocol.MESSAGE_REMOVEDOCUMENT: {
            RemoveDocumentMessage removeMsg = (RemoveDocumentMessage)msg;
            if (removeMsg.getDocumentId().hasDocType()) {
                return selector.accepts(removeMsg.getDocumentRemove()) != Result.FALSE;
            } else {
                return true;
            }
        }

        case DocumentProtocol.MESSAGE_BATCHDOCUMENTUPDATE:
            BatchDocumentUpdateMessage bdu = (BatchDocumentUpdateMessage)msg;
            for (int i = 0; i < bdu.getUpdates().size(); i++) {
                if (selector.accepts(bdu.getUpdates().get(i)) == Result.FALSE) {
                    return false;
                }
            }
            return true;

        default:
            return true;
        }
    }

    @Override
    public void merge(RoutingContext context) {
        DocumentProtocol.merge(context);
    }

    @Override
    public void destroy() {
        if (subscriber != null) {
            subscriber.close();
        }
    }

    @Override
    public MetricSet getMetrics() {
        return null;
    }
}
