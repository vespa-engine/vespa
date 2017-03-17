// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.index.indexmanagerinitializer");
#include "index_manager_initializer.h"
#include <vespa/vespalib/io/fileutil.h>

namespace proton
{

IndexManagerInitializer::
IndexManagerInitializer(const vespalib::string &baseDir,
                        const searchcorespi::index::WarmupConfig & warmupCfg,
                        size_t maxFlushed,
                        size_t cacheSize,
                        const search::index::Schema &schema,
                        searchcorespi::IIndexManager::Reconfigurer & reconfigurer,
                        searchcorespi::index::IThreadingService & threadingService,
                        vespalib::ThreadExecutor & warmupExecutor,
                        const search::TuneFileIndexManager & tuneFileIndexManager,
                        const search::TuneFileAttributes &tuneFileAttributes,
                        const search::common::FileHeaderContext & fileHeaderContext,
                        std::shared_ptr<searchcorespi::IIndexManager::SP> indexManager)
    : _baseDir(baseDir),
      _warmupCfg(warmupCfg),
      _maxFlushed(maxFlushed),
      _cacheSize(cacheSize),
      _schema(schema),
      _reconfigurer(reconfigurer),
      _threadingService(threadingService),
      _warmupExecutor(warmupExecutor),
      _tuneFileIndexManager(tuneFileIndexManager),
      _tuneFileAttributes(tuneFileAttributes),
      _fileHeaderContext(fileHeaderContext),
      _indexManager(indexManager)
{
}


void
IndexManagerInitializer::run()
{
    LOG(debug, "About to create proton::IndexManager with %u index field(s)",
        _schema.getNumIndexFields());
    vespalib::mkdir(_baseDir, false);
    *_indexManager = std::make_shared<proton::IndexManager>
                    (_baseDir,
                     _warmupCfg,
                     _maxFlushed,
                     _cacheSize,
                     _schema,
                     _reconfigurer,
                     _threadingService,
                     _warmupExecutor,
                     _tuneFileIndexManager,
                     _tuneFileAttributes,
                     _fileHeaderContext);
}


} // namespace proton
