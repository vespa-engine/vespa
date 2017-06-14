// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::api::WritableDocumentList
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

#include <vespa/vdslib/container/mutabledocumentlist.h>

namespace vdslib {

class WritableDocumentList : public MutableDocumentList {
public:
    /**
     * Create a new docblock, using the given buffer.
     * @param keepexisting If set to true, assume buffer is already filled.
     */
    WritableDocumentList(const document::DocumentTypeRepo::SP & repo, char* buffer, uint32_t bufferSize, bool keepexisting = false);

    WritableDocumentList(const DocumentList& source, char* buffer, uint32_t bufferSize);
    /**
     * Prepare a multiput/remove.
     * Returns a char* to the part of the buffer you can write contentSize
     * data to. (Both header and body data), 0 if not enough space.
     */
    char* prepareMultiput(uint32_t docCount, uint32_t contentSize);
    /**
     * Commit a multiput/remove. Call this after you've written all content to
     * contentPos gotten from prepareMultiput(). Give relative positions from
     * contentPos in meta entries.
     */
    bool commitMultiput(const std::vector<MetaEntry>& meta, char* contentPos);

};

} // vdslib

