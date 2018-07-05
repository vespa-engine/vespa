// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;

import java.time.Duration;

/**
 * <p>A session for synchronous access to a document repository. This class
 * provides simple document access where throughput is not a concern.</p>
 *
 * @author Simon Thoresen Hult
 * @author bjorncs
 */
public interface SyncSession extends Session {

    /**
     * <p>Puts a document. When this method returns, the document is safely
     * received. This enables setting condition compared to using Document.</p>
     *
     * @param documentPut The DocumentPut operation
     */
    void put(DocumentPut documentPut);

    /**
     * <p>Puts a document. When this method returns, the document is safely
     * received.</p>
     *
     * @param documentPut The DocumentPut operation
     * @param priority The priority with which to perform this operation.
     */
    void put(DocumentPut documentPut, DocumentProtocol.Priority priority);

    /**
     * <p>Gets a document.</p>
     *
     * @param id The id of the document to get.
     * @return The known document having this id, or null if there is no
     *         document having this id.
     * @throws UnsupportedOperationException Thrown if this access does not
     *                                       support retrieving.
     */
    Document get(DocumentId id);

    /**
     * <p>Gets a document.</p>
     *
     * @param id       The id of the document to get.
     * @param fieldSet A comma-separated list of fields to retrieve
     * @param priority The priority with which to perform this operation.
     * @return The known document having this id, or null if there is no
     *         document having this id.
     * @throws UnsupportedOperationException Thrown if this access does not
     *                                       support retrieving.
     */
    Document get(DocumentId id, String fieldSet, DocumentProtocol.Priority priority);

    /**
     * <p>Gets a document with timeout.</p>
     *
     * @param id The id of the document to get.
     * @param timeout Timeout. If timeout is null, an unspecified default will be used.
     * @return The known document having this id, or null if there is no
     *         document having this id.
     * @throws UnsupportedOperationException Thrown if this access does not support retrieving.
     * @throws DocumentAccessException on any messagebus error, including timeout ({@link com.yahoo.messagebus.ErrorCode#TIMEOUT}).
     */
    // TODO Vespa 7: Remove default implementation. Consider removing get() overloads without timeout.
    default Document get(DocumentId id, Duration timeout) {
        return get(id);
    }

    /**
     * <p>Gets a document with timeout. </p>
     *
     * @param id       The id of the document to get.
     * @param fieldSet A comma-separated list of fields to retrieve
     * @param priority The priority with which to perform this operation.
     * @param timeout Timeout. If timeout is null, an unspecified default will be used.
     * @return The known document having this id, or null if there is no
     *         document having this id.
     * @throws UnsupportedOperationException Thrown if this access does not support retrieving.
     * @throws DocumentAccessException on any messagebus error, including timeout ({@link com.yahoo.messagebus.ErrorCode#TIMEOUT}).
     */
    // TODO Vespa 7: Remove default implementation. Consider removing get() overloads without timeout.
    default Document get(DocumentId id, String fieldSet, DocumentProtocol.Priority priority, Duration timeout) {
        return get(id, fieldSet, priority);
    }

    /**
     * <p>Removes a document if it is present and condition is fulfilled.</p>
     * @param documentRemove document to delete
     * @return true If the document with this id was removed, false otherwise.
     */
    boolean remove(DocumentRemove documentRemove);

    /**
     * <p>Removes a document if it is present.</p>
     *
     * @param documentRemove Document remove operation
     * @param priority The priority with which to perform this operation.
     * @return true If the document with this id was removed, false otherwise.
     * @throws UnsupportedOperationException Thrown if this access does not
     *                                       support removal.
     */
    boolean remove(DocumentRemove documentRemove, DocumentProtocol.Priority priority);

    /**
     * <p>Updates a document.</p>
     *
     * @param update The updates to perform.
     * @return True, if the document was found and updated.
     * @throws UnsupportedOperationException Thrown if this access does not
     *                                       support update.
     */
    boolean update(DocumentUpdate update);

    /**
     * <p>Updates a document.</p>
     *
     * @param update   The updates to perform.
     * @param priority The priority with which to perform this operation.
     * @return True, if the document was found and updated.
     * @throws UnsupportedOperationException Thrown if this access does not
     *                                       support update.
     */
    boolean update(DocumentUpdate update, DocumentProtocol.Priority priority);

}
