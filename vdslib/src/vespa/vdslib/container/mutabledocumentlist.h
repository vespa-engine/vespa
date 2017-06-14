// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::api::MutableDocumentList
 * @ingroup messageapi
 *
 * @brief A utility class for a buffer containing a list of documents.
 *
 * When writing to the docblock, it will typically be filled up from the end
 * and forwards, until the free gap between the meta entry list and the data
 * it uses, is so small that no more entry fits.
 *
 * @version $Id$
 */

#pragma once

#include "documentlist.h"
#include "operationlist.h"

namespace vdslib {

class MutableDocumentList : public DocumentList {
public:
    /**
     * Create a new docblock, using the given buffer.
     * @param keepexisting If set to true, assume buffer is already filled.
     */
    MutableDocumentList(const document::DocumentTypeRepo::SP & repo, char* buffer, uint32_t bufferSize, bool keepexisting = false);

    MutableDocumentList(const DocumentList& source, char* buffer, uint32_t bufferSize);

    // Want to take const pointers to docs here, but can't since we need to
    // call serializeHeader/Body.. Grmpf..

    /** Returns false if no more space in docblock. (Entry not added) */
    bool addPut(const document::Document&, Timestamp = 0, bool addBody = true);
    /** Returns false if no more space in docblock. (Entry not added) */
    bool addRemove(const document::DocumentId& docId, Timestamp = 0);
    bool addEntry(const DocumentList::Entry& inEntry);
    bool addEntry(const DocumentList::Entry& inEntry, Timestamp ts);
    bool addOperationList(const OperationList& list);
    bool addUpdate(const document::DocumentUpdate&, Timestamp = 0);
};

} // vdslib

