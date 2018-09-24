// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentdb_tagged_metrics.h"

namespace proton {

/**
 * A collection of all the metrics for a document db (both tagged and no-tagged).
 */
class DocumentDBMetricsCollection
{
private:
    DocumentDBTaggedMetrics _taggedMetrics;
    size_t _maxNumThreads;

public:
    DocumentDBMetricsCollection(const vespalib::string &docTypeName, size_t maxNumThreads);
    ~DocumentDBMetricsCollection();
    DocumentDBTaggedMetrics &getTaggedMetrics() { return _taggedMetrics; }
    size_t maxNumThreads() const { return _maxNumThreads; }
};

}

