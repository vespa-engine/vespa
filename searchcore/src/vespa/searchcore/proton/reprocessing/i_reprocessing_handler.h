// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_reprocessing_reader.h"
#include "i_reprocessing_rewriter.h"

namespace proton {

/**
 * Interface for a handler of a particular reprocessing job.
 * Readers and rewriters are registered to this handler to receive all documents being reprocessed.
 */
struct IReprocessingHandler
{
    virtual ~IReprocessingHandler() {}

    /**
     * Adds the given processing reader to this handler.
     */
    virtual void addReader(const IReprocessingReader::SP &reader) = 0;

    /**
     * Adds the given processing rewriter to this handler.
     */
    virtual void addRewriter(const IReprocessingRewriter::SP &rewriter) = 0;
};

} // namespace proton

