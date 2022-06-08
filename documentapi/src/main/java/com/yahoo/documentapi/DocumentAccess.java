// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentTypeManagerConfigurer;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.documentapi.messagebus.MessageBusDocumentAccess;

/**
 * <p>This is the starting point of the <b>document api</b>. This api provides
 * access to documents in a document repository. The document api contains four
 * separate access types: </p>
 *
 * <ul>
 * <li><b>Synchronous random access</b> - provided by {@link SyncSession},
 * allows simple access where throughput is not a concern.
 * <li><b>Asynchronous random access</b> - provided by {@link AsyncSession},
 * allows document repository writes and random access with high throughput.
 * <li><b>Visiting</b> - provided by {@link VisitorSession}, allows a set of
 * documents to be accessed in an order decided by the document repository. This
 * allows much higher read throughput than random access.
 * <li><b>Subscription</b> - provided by {@link SubscriptionSession}, allows
 * changes to a defined set of documents in the repository to be
 * visited.
 * </ul>
 *
 * <p>This class is the factory for creating the four session types mentioned above.</p>
 *
 * <p>There may be multiple implementations of the document api classes. If
 * default configuration is sufficient, simply inject a {@code DocumentAccess} to
 * obtain a running document access. If you instead create a concrete implementation, note that
 * there are running threads within an access object, so you must shut it down when done.</p>
 *
 * <p>An implementation of the Document Api may support just a subset of the
 * access types defined in this interface. For example, some document
 * repositories, like indexes, are <i>write only</i>. Others may support random
 * access, but not visiting and subscription. Any method which is not supported
 * by the underlying implementation will throw UnsupportedOperationException.</p>
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
     * This is a convenience method to return a document access object when running
     * outside of a Vespa application container, with all default parameter values.
     * The client that calls this method is also responsible for shutting the object
     * down when done. If an error occurred while attempting to create such an object,
     * this method will throw an exception.
     * This document access requires new config subscriptions to be set up, which should
     * be avoided in application containers, but is suitable for, e.g., CLIs.
     *
     * @return a running document access object with all default configuration
     */
    public static DocumentAccess createForNonContainer() {
        return new MessageBusDocumentAccess();
    }

    /**
     * Constructs a new document access object.
     *
     * @param params the parameters to use for setup
     */
    protected DocumentAccess(DocumentAccessParams params) {
        if (params.documentmanagerConfig().isPresent()) { // our config has been injected into the creator
            documentTypeManager = new DocumentTypeManager(params.documentmanagerConfig().get());
            documentTypeConfigSubscriber = null;
        }
        else { // fallback to old style subscription — this should be avoided
            documentTypeManager = new DocumentTypeManager();
            documentTypeConfigSubscriber = DocumentTypeManagerConfigurer.configure(documentTypeManager, params.getDocumentManagerConfigId());
        }
    }

    /**
     * Returns a session for synchronous document access. Use this for simple access.
     *
     * @param parameters the parameters of this sync session
     * @return a session to use for synchronous document access
     * @throws UnsupportedOperationException if this access implementation does not support synchronous access
     * @throws RuntimeException              if an error prevented the session from being created.
     */
    public abstract SyncSession createSyncSession(SyncParameters parameters);

    /**
     * Returns a session for asynchronous document access. Use this if high operation throughput is required.
     *
     * @param parameters the parameters of this async session.
     * @return a session to use for asynchronous document access.
     * @throws UnsupportedOperationException if this access implementation does not support asynchronous access.
     * @throws RuntimeException              if an error prevented the session from being created
     */
    public abstract AsyncSession createAsyncSession(AsyncParameters parameters);

    /**
     * Run a visitor with the given visitor parameters, and get the result back here.
     *
     * @param parameters The parameters of this visitor session.
     * @return a session used to track progress of the visitor and get the actual data returned.
     * @throws UnsupportedOperationException if this access implementation does not support visiting
     * @throws RuntimeException              if an error prevented the session from being created
     * @throws ParseException                if the document selection string could not be parsed
     */
    public abstract VisitorSession createVisitorSession(VisitorParameters parameters) throws ParseException;

    /**
     * Creates a destination session for receiving data from visiting.
     * The visitor must be started and progress tracked through a visitor session.
     *
     * @param parameters the parameters of this visitor destination session
     * @return a session used to get the actual data returned
     * @throws UnsupportedOperationException if this access implementation does not support visiting.
     */
    public abstract VisitorDestinationSession createVisitorDestinationSession(VisitorDestinationParameters parameters);

    /**
     * Creates a subscription and returns a session for getting data from it.
     * Use this to get document operations being done by other parties.
     *
     * @param parameters The parameters of this subscription session
     * @return a session to use for document subscription
     * @throws UnsupportedOperationException if this access implementation does not support subscription
     * @throws RuntimeException              if an error prevented the session from being created
     */
    public abstract SubscriptionSession createSubscription(SubscriptionParameters parameters);

    /**
     * Returns a session for document subscription. Use this to get document operations being done by other parties.
     *
     * @param parameters the parameters of this subscription session
     * @return a session to use for document subscription
     * @throws UnsupportedOperationException if this access implementation does not support subscription
     * @throws RuntimeException              if an error prevented the session from being created
     */
    public abstract SubscriptionSession openSubscription(SubscriptionParameters parameters);

    /**
     * Shuts down the underlying sessions used by this DocumentAccess;
     * subsequent use of this DocumentAccess will throw unspecified exceptions, depending on implementation.
     * Classes overriding this must call super.shutdown().
     */
    public void shutdown() {
        if (documentTypeConfigSubscriber != null)
            documentTypeConfigSubscriber.close();
    }

    /** Returns the {@link DocumentTypeManager} used by this DocumentAccess. */
    public DocumentTypeManager getDocumentTypeManager() { return documentTypeManager; }

}
