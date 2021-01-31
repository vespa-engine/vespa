// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "summarymanagerinitializer.h"
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/vespalib/io/fileutil.h>

namespace proton {

SummaryManagerInitializer::
SummaryManagerInitializer(const search::GrowStrategy &grow,
                          const vespalib::string & baseDir,
                          const vespalib::string &subDbName,
                          const DocTypeName &docTypeName,
                          vespalib::ThreadExecutor &summaryExecutor,
                          const search::LogDocumentStore::Config & storeCfg,
                          const search::TuneFileSummary &tuneFile,
                          const search::common::FileHeaderContext &fileHeaderContext,
                          search::transactionlog::SyncProxy &tlSyncer,
                          IBucketizerSP bucketizer,
                          std::shared_ptr<SummaryManager::SP> result)
    : proton::initializer::InitializerTask(),
      _grow(grow),
      _baseDir(baseDir),
      _subDbName(subDbName),
      _docTypeName(docTypeName),
      _summaryExecutor(summaryExecutor),
      _storeCfg(storeCfg),
      _tuneFile(tuneFile),
      _fileHeaderContext(fileHeaderContext),
      _tlSyncer(tlSyncer),
      _bucketizer(std::move(bucketizer)),
      _result(std::move(result))
{ }

SummaryManagerInitializer::~SummaryManagerInitializer() = default;

void
SummaryManagerInitializer::run()
{
    vespalib::mkdir(_baseDir, false);
    vespalib::Timer timer;
    EventLogger::loadDocumentStoreStart(_subDbName);
    *_result = std::make_shared<SummaryManager>
               (_summaryExecutor, _storeCfg, _grow, _baseDir, _docTypeName,
                _tuneFile, _fileHeaderContext, _tlSyncer, _bucketizer);
    EventLogger::loadDocumentStoreComplete(_subDbName, timer.elapsed());
}

} // namespace proton
