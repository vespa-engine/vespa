// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.index.indexmanager");

#include "indexmanager.h"
#include "diskindexwrapper.h"
#include "memoryindexwrapper.h"
#include <vespa/searchlib/common/serialnumfileheadercontext.h>
#include <vespa/searchlib/diskindex/fusion.h>

using search::diskindex::Fusion;
using search::common::FileHeaderContext;
using search::common::SerialNumFileHeaderContext;
using search::index::Schema;
using search::index::SchemaUtil;
using search::TuneFileIndexing;
using search::TuneFileIndexManager;
using search::TuneFileSearch;
using searchcorespi::index::IDiskIndex;
using search::diskindex::SelectorArray;
using searchcorespi::index::IndexMaintainerConfig;
using searchcorespi::index::WarmupConfig;
using searchcorespi::index::IndexMaintainerContext;
using searchcorespi::index::IMemoryIndex;
using searchcorespi::index::IThreadingService;

namespace proton {

IndexManager::MaintainerOperations::MaintainerOperations(const FileHeaderContext &fileHeaderContext,
                                                         const TuneFileIndexManager &tuneFileIndexManager,
                                                         size_t cacheSize,
                                                         searchcorespi::index::
                                                         IThreadingService &
                                                         threadingService)
    : _cacheSize(cacheSize),
      _fileHeaderContext(fileHeaderContext),
      _tuneFileIndexing(tuneFileIndexManager._indexing),
      _tuneFileSearch(tuneFileIndexManager._search),
      _threadingService(threadingService)
{
}

IMemoryIndex::SP
IndexManager::MaintainerOperations::createMemoryIndex(const Schema &schema,
                                                      SerialNum serialNum)
{
    return IMemoryIndex::SP(new MemoryIndexWrapper(schema,
                                                   _fileHeaderContext,
                                                   _tuneFileIndexing,
                                                   _threadingService,
                                                   serialNum));
}

IDiskIndex::SP
IndexManager::MaintainerOperations::loadDiskIndex(const vespalib::string &indexDir)
{
    return IDiskIndex::SP(new DiskIndexWrapper(indexDir, _tuneFileSearch, _cacheSize));
}

IDiskIndex::SP
IndexManager::MaintainerOperations::reloadDiskIndex(const IDiskIndex &oldIndex)
{
    return IDiskIndex::SP(new DiskIndexWrapper(dynamic_cast<const DiskIndexWrapper &>(oldIndex),
                                               _tuneFileSearch, _cacheSize));
}

bool
IndexManager::MaintainerOperations::runFusion(const Schema &schema,
                                              const vespalib::string &outputDir,
                                              const std::vector<vespalib::string> &sources,
                                              const SelectorArray &selectorArray,
                                              SerialNum serialNum)
{
    SerialNumFileHeaderContext fileHeaderContext(_fileHeaderContext,
                                                 serialNum);
    const bool dynamic_k_doc_pos_occ_format = false;
    return Fusion::merge(schema, outputDir, sources, selectorArray,
                         dynamic_k_doc_pos_occ_format,
                         _tuneFileIndexing, fileHeaderContext);
}


IndexManager::IndexManager(const vespalib::string &baseDir,
                           const WarmupConfig & warmup,
                           const size_t maxFlushed,
                           const size_t cacheSize,
                           const Schema &schema,
                           SerialNum serialNum,
                           Reconfigurer &reconfigurer,
                           IThreadingService &threadingService,
                           vespalib::ThreadExecutor & warmupExecutor,
                           const search::TuneFileIndexManager &tuneFileIndexManager,
                           const search::TuneFileAttributes &tuneFileAttributes,
                           const search::common::FileHeaderContext &fileHeaderContext) :
    _operations(fileHeaderContext, tuneFileIndexManager, cacheSize,
                threadingService),
    _maintainer(IndexMaintainerConfig(baseDir,
                                      warmup,
                                      maxFlushed,
                                      schema,
                                      serialNum,
                                      tuneFileAttributes),
                IndexMaintainerContext(threadingService,
                                       reconfigurer,
                                       fileHeaderContext,
                                       warmupExecutor),
                _operations)
{
}

IndexManager::~IndexManager()
{
}

} // namespace proton

