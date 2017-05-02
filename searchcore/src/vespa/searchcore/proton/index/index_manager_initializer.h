// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "indexmanager.h"
#include <vespa/searchcore/proton/initializer/initializer_task.h>

namespace proton
{


/*
 * Class representing an initializer task for constructing index manager
 * during proton startup.
 */
class IndexManagerInitializer :  public initializer::InitializerTask
{
    const vespalib::string                      _baseDir;
    const searchcorespi::index::WarmupConfig    _warmupCfg;
    size_t                                      _maxFlushed;
    size_t                                      _cacheSize;
    const search::index::Schema                 _schema;
    search::SerialNum                           _serialNum;
    searchcorespi::IIndexManager::Reconfigurer &_reconfigurer;
    searchcorespi::index::IThreadingService    &_threadingService;
    vespalib::ThreadExecutor                   &_warmupExecutor;
    const search::TuneFileIndexManager          _tuneFileIndexManager;
    const search::TuneFileAttributes            _tuneFileAttributes;
    const search::common::FileHeaderContext    &_fileHeaderContext;
    std::shared_ptr<searchcorespi::IIndexManager::SP> _indexManager;
public:
    // Note: lifetime of indexManager must be handled by caller.
    IndexManagerInitializer(const vespalib::string &baseDir,
                            const searchcorespi::index::WarmupConfig & warmupCfg,
                            size_t maxFlushed,
                            size_t cacheSize,
                            const search::index::Schema &schema,
                            search::SerialNum serialNum,
                            searchcorespi::IIndexManager::Reconfigurer & reconfigurer,
                            searchcorespi::index::IThreadingService & threadingService,
                            vespalib::ThreadExecutor & warmupExecutor,
                            const search::TuneFileIndexManager & tuneFileIndexManager,
                            const search::TuneFileAttributes & tuneFileAttributes,
                            const search::common::FileHeaderContext & fileHeaderContext,
                            std::shared_ptr<searchcorespi::IIndexManager::SP> indexManager);
    virtual void run() override;
};

} // namespace proton
