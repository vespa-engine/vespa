// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "index_manager_initializer.h"
#include <vespa/vespalib/io/fileutil.h>
#include <filesystem>

#include <vespa/log/log.h>
LOG_SETUP(".proton.index.indexmanagerinitializer");

namespace proton {

IndexManagerInitializer::
IndexManagerInitializer(const std::string &baseDir,
                        const index::IndexConfig & indexCfg,
                        const search::index::Schema &schema,
                        search::SerialNum serialNum,
                        searchcorespi::IIndexManager::Reconfigurer & reconfigurer,
                        searchcorespi::index::IThreadingService & threadingService,
                        vespalib::Executor & warmupExecutor,
                        const search::TuneFileIndexManager & tuneFileIndexManager,
                        const search::TuneFileAttributes &tuneFileAttributes,
                        const search::common::FileHeaderContext & fileHeaderContext,
                        std::shared_ptr<search::diskindex::IPostingListCache> posting_list_cache,
                        std::shared_ptr<searchcorespi::IIndexManager::SP> indexManager)
    : _baseDir(baseDir),
      _indexConfig(indexCfg),
      _schema(schema),
      _serialNum(serialNum),
      _reconfigurer(reconfigurer),
      _threadingService(threadingService),
      _warmupExecutor(warmupExecutor),
      _tuneFileIndexManager(tuneFileIndexManager),
      _tuneFileAttributes(tuneFileAttributes),
      _fileHeaderContext(fileHeaderContext),
      _posting_list_cache(std::move(posting_list_cache)),
      _indexManager(indexManager)
{
}

IndexManagerInitializer::~IndexManagerInitializer() = default;

void
IndexManagerInitializer::run()
{
    LOG(debug, "About to create proton::IndexManager with %u index field(s)", _schema.getNumIndexFields());
    std::filesystem::create_directory(std::filesystem::path(_baseDir));
    vespalib::File::sync(vespalib::dirname(_baseDir));
    *_indexManager = std::make_shared<index::IndexManager>
                    (_baseDir, _posting_list_cache, _indexConfig, _schema, _serialNum, _reconfigurer, _threadingService,
                     _warmupExecutor, _tuneFileIndexManager, _tuneFileAttributes, _fileHeaderContext);
}


} // namespace proton
