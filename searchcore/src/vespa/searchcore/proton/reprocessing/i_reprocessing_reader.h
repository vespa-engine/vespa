// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/fieldvalue/document.h>

namespace proton {

/**
 * Interface for a reprocessor that handles a set of documents
 * to update some other components based on the content of those documents.
 */
struct IReprocessingReader
{
    typedef std::shared_ptr<IReprocessingReader> SP;

    virtual ~IReprocessingReader() {}

    /**
     * Handle the given existing document.
     */
    virtual void handleExisting(uint32_t lid, const std::shared_ptr<document::Document> &doc) = 0;

    // signals that there are no more documents
    virtual void done() {}
};

} // namespace proton

