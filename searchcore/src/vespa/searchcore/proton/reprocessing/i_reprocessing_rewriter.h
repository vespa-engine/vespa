// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/fieldvalue/document.h>

namespace proton {

/**
 * Interface for a reprocessor that rewrites a set of documents
 * based on the content of other underlying components.
 */
struct IReprocessingRewriter
{
    using SP = std::shared_ptr<IReprocessingRewriter>;

    virtual ~IReprocessingRewriter() {}

    /**
     * Handle and rewrite the given existing document.
     */
    virtual void handleExisting(uint32_t lid, const std::shared_ptr<document::Document> &doc) = 0;
};

} // namespace proton

