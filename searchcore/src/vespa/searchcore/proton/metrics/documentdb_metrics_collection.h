// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentdb_tagged_metrics.h"
#include "legacy_documentdb_metrics.h"

namespace proton {

/**
 * A collection of all the metrics for a document db (both tagged and no-tagged).
 */
class DocumentDBMetricsCollection
{
private:
    LegacyDocumentDBMetrics _metrics;
    DocumentDBTaggedMetrics _taggedMetrics;

public:
    DocumentDBMetricsCollection(const vespalib::string &docTypeName, size_t maxNumThreads);
    ~DocumentDBMetricsCollection();
    LegacyDocumentDBMetrics &getLegacyMetrics() { return _metrics; }
    DocumentDBTaggedMetrics &getTaggedMetrics() { return _taggedMetrics; }
};

} // namespace proton

