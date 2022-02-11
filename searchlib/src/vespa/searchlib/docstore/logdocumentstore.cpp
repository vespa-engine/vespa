// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "logdocumentstore.h"

namespace search {

using vespalib::nbostream;
using common::FileHeaderContext;

bool
LogDocumentStore::Config::operator == (const Config & rhs) const {
    (void) rhs;
    return DocumentStore::Config::operator ==(rhs) && (_logConfig == rhs._logConfig);
}

LogDocumentStore::LogDocumentStore(vespalib::Executor & executor,
                                   const vespalib::string & baseDir,
                                   const Config & config,
                                   const GrowStrategy & growStrategy,
                                   const TuneFileSummary & tuneFileSummary,
                                   const FileHeaderContext &fileHeaderContext,
                                   transactionlog::SyncProxy &tlSyncer,
                                   IBucketizer::SP bucketizer)
    : DocumentStore(config, _backingStore),
      _backingStore(executor, baseDir, config.getLogConfig(), growStrategy,
                    tuneFileSummary, fileHeaderContext, tlSyncer, std::move(bucketizer))
{}

LogDocumentStore::~LogDocumentStore() = default;

void
LogDocumentStore::reconfigure(const Config & config) {
    DocumentStore::reconfigure(config);
    _backingStore.reconfigure(config.getLogConfig());
}

} // namespace search

