// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentdb_metrics_collection.h"

namespace proton {

DocumentDBMetricsCollection::DocumentDBMetricsCollection(const vespalib::string &docTypeName, size_t maxNumThreads)
    : _taggedMetrics(docTypeName),
      _maxNumThreads(maxNumThreads)
{}

DocumentDBMetricsCollection::~DocumentDBMetricsCollection() = default;

}

