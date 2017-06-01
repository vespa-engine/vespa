// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "logdocumentstore.h"

namespace search {

using vespalib::nbostream;
using common::FileHeaderContext;

LogDocumentStore::LogDocumentStore(vespalib::ThreadExecutor & executor,
                                   const vespalib::string & baseDir,
                                   const Config & config,
                                   const GrowStrategy & growStrategy,
                                   const TuneFileSummary & tuneFileSummary,
                                   const FileHeaderContext &fileHeaderContext,
                                   transactionlog::SyncProxy &tlSyncer,
                                   const IBucketizer::SP & bucketizer)
    : DocumentStore(config, _backingStore),
      _backingStore(executor, baseDir, config.getLogConfig(), growStrategy,
                    tuneFileSummary, fileHeaderContext, tlSyncer, bucketizer)
{
}

LogDocumentStore::~LogDocumentStore()
{
}

} // namespace search

