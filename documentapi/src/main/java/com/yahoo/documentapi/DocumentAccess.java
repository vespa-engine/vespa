// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentTypeManagerConfigurer;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.config.subscription.ConfigSubscriber;

/**
 * <p>This is the starting point of the <b>document api</b>. This api provides
 * access to documents in a document repository. The document api contains four
 * separate access types: </p>
 *
 * <ul><li><b>Synchronous random access</b> - provided by {@link SyncSession},
 * allows simple access where throughput is not a concern.</li>
 * <li><b>Asynchronous random access</b> - provided by {@link AsyncSession},
 * allows document repository writes and random access with high
 * throughput.</li>
 * <li><b>Visiting</b> - provided by {@link VisitorSession}, allows a set of
 * documents to be accessed in an order decided by the document repository. This
 * allows much higher read throughput than random access.</li>
 * <li><b>Subscription</b> - provided by {@link SubscriptionSession}, allows
 * changes to a defined set of documents in the repository to be
 * visited.</li></ul>
 *
 * <p>This class is the factory for creating the four session types mentioned
 * above.</p>
 *
 * <p>There may be multiple implementations of the document api classes. If
 * default configuration is sufficient, use the {@link #createDefault} method to
 * return a running document access. Note that there are running threads within
 * an access object, so you must shut it down when done.</p>
 *
 * <p>An implementation of the Document Api may support just a subset of the
 * access types defined in this interface. For example, some document
 * repositories, like indexes, are <i>write only</i>. Others may support random
 * access, but not visiting and subscription. Any method which is not supported
 * by the underlying implementation will throw
 * UnsupportedOperationException.</p>
 *
 * <p>Access to this class is thread-safe.</p>
 *
 * @author bratseth
 * @author Einar Rosenvinge
 * @author Simon Thoresen Hult
 */
public abstract class DocumentAccess {

    private final DocumentTypeManager documentTypeManager;
    private final ConfigSubscriber documentTypeConfigSubscriber;

    /**
     * <p>This is a convenience method to return a document access object with
     * all default parameter values. The client that calls this method is also
     * responsible for shutting the object down when done. If an error occurred
     * while attempting to create such an object, this method will throw an
     * exception.</p>
     *
     * @return A running document access object with all default configuration.
     */
    public static DocumentAccess createDefault() {
        return new com.yahoo.documentapi.messagebus.MessageBusDocumentAccess();
    }

    /**
     * <p>Constructs a new document access object.</p>
     *
     * @param params The parameters to use for setup.
     */
    protected DocumentAccess(DocumentAccessParams params) {
        super();
        if (params.documentmanagerConfig().isPresent()) { // our config has been injected into the creator
            documentTypeManager = new DocumentTypeManager(params.documentmanagerConfig().get());
            documentTypeConfigSubscriber = null;
        }
        else { // fallback to old style subscription
            documentTypeManager = new DocumentTypeManager();
            documentTypeConfigSubscriber = DocumentTypeManagerConfigurer.configure(documentTypeManager, params.getDocumentManagerConfigId());
        }
    }

    /**
     * <p>Returns a session for synchronous document access. Use this for simple
     * access.</p>
     *
     * @param parameters The parameters of this sync session.
     * @return A session to use for synchronous document access.
     * @throws UnsupportedOperationException If this access implementation does
     *                                       not support synchronous access.
     * @throws RuntimeException              If an error prevented the session
     *                                       from being created.
     */
    public abstract SyncSession createSyncSession(SyncParameters parameters);

    /**
     * <p>Returns a session for asynchronous document access. Use this if high
     * operation throughput is required.</p>
     *
     * @param parameters The parameters of this async session.
     * @return A session to use for asynchronous document access.
     * @throws UnsupportedOperationException If this access implementation does
     *                                       not support asynchronous access.
     * @throws RuntimeException              If an error prevented the session
     *                                       from being created.
     */
    public abstract AsyncSession createAsyncSession(AsyncParameters parameters);

    /**
     * <p>Run a visitor with the given visitor parameters, and get the result
     * back here.</p>
     *
     * @param parameters The parameters of this visitor session.
     * @return A session used to track progress of the visitor and get the
     *         actual data returned.
     * @throws UnsupportedOperationException If this access implementation does
     *                                       not support visiting.
     * @throws RuntimeException              If an error prevented the session
     *                                       from being created.
     * @throws ParseException                If the document selection string
     *                                       could not be parsed.
     */
    public abstract VisitorSession createVisitorSession(VisitorParameters parameters) throws ParseException;

    /**
     * <p>Creates a destination session for receiving data from visiting. The
     * visitor must be started and progress tracked through a visitor
     * session.</p>
     *
     * @param parameters The parameters of this visitor destination session.
     * @return A session used to get the actual data returned.
     * @throws UnsupportedOperationException If this access implementation does
     *                                       not support visiting.
     */
    public abstract VisitorDestinationSession createVisitorDestinationSession(VisitorDestinationParameters parameters);

    /**
     * <p>Creates a subscription and returns a session for getting data from
     * it. Use this to get document operations being done by other parties.</p>
     *
     * @param parameters The parameters of this subscription session.
     * @return A session to use for document subscription.
     * @throws UnsupportedOperationException If this access implementation does
     *                                       not support subscription.
     * @throws RuntimeException              If an error prevented the session
     *                                       from being created.
     */
    public abstract SubscriptionSession createSubscription(SubscriptionParameters parameters);

    /**
     * <p>Returns a session for document subscription. Use this to get document
     * operations being done by other parties.</p>
     *
     * @param parameters The parameters of this subscription session.
     * @return A session to use for document subscription.
     * @throws UnsupportedOperationException If this access implementation does
     *                                       not support subscription.
     * @throws RuntimeException              If an error prevented the session
     *                                       from being created.
     */
    public abstract SubscriptionSession openSubscription(SubscriptionParameters parameters);

    /**
     * Shuts down the underlying sessions used by this DocumentAccess;
     * subsequent use of this DocumentAccess will throw unspecified exceptions,
     * depending on implementation.
     * Classes overriding this must call super.shutdown().
     */
    public void shutdown() {
        if (documentTypeConfigSubscriber != null)
            documentTypeConfigSubscriber.close();
    }

    /**
     * <p>Returns the {@link DocumentTypeManager} used by this
     * DocumentAccess.</p>
     *
     * @return The document type manager.
     */
    public DocumentTypeManager getDocumentTypeManager() {
        return documentTypeManager;
    }
}
