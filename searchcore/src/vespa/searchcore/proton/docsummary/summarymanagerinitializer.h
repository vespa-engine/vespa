// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "summarymanager.h"
#include <vespa/searchcore/proton/initializer/initializer_task.h>
#include <vespa/searchcommon/common/growstrategy.h>

namespace proton {

/*
 * Class representing an initializer task for constructing summary manager
 * during proton startup.
 */
class SummaryManagerInitializer : public initializer::InitializerTask
{
    using IBucketizerSP = std::shared_ptr<search::IBucketizer>;
    const search::GrowStrategy               _grow;
    const vespalib::string                   _baseDir;
    const vespalib::string                   _subDbName;
    vespalib::Executor                      &_shared_executor;
    const search::LogDocumentStore::Config   _storeCfg;
    const search::TuneFileSummary            _tuneFile;
    const search::common::FileHeaderContext &_fileHeaderContext;
    search::transactionlog::SyncProxy       &_tlSyncer;
    const IBucketizerSP                      _bucketizer;
    std::shared_ptr<SummaryManager::SP>      _result;

public:
    using SP = std::shared_ptr<SummaryManagerInitializer>;

    // Note: lifetime of result must be handled by caller.
    SummaryManagerInitializer(const search::GrowStrategy &grow,
                              const vespalib::string & baseDir,
                              const vespalib::string &subDbName,
                              vespalib::Executor &shared_executor,
                              const search::LogDocumentStore::Config & storeCfg,
                              const search::TuneFileSummary &tuneFile,
                              const search::common::FileHeaderContext & fileHeaderContext,
                              search::transactionlog::SyncProxy &tlSyncer,
                              IBucketizerSP bucketizer,
                              std::shared_ptr<SummaryManager::SP> result);
    ~SummaryManagerInitializer() override;
    void run() override;
};

} // namespace proton
