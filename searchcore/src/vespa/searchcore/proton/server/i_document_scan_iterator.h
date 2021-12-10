// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/idocumentmetastore.h>

namespace proton {

/**
 * Iterator for scanning all documents in a document sub db to find candidates
 * for moving as part of lid space compaction.
 */
struct IDocumentScanIterator
{
    typedef std::unique_ptr<IDocumentScanIterator> UP;

    virtual ~IDocumentScanIterator() = default;

    /**
     * Returns false if we are certain there are no more documents to scan, true otherwise.
     * Returning false should only happen after a call to next() has returned an invalid document.
     */
    virtual bool valid() const = 0;

    /**
     * Returns the next document that has lid > compactLidLimit to be moved.
     * Returns an invalid document if no documents satisfy the limit.
     *
     * @param compactLidLimit The returned document must have lid larger than this limit.
     */
    virtual search::DocumentMetaData next(uint32_t compactLidLimit) = 0;
};

} // namespace proton

