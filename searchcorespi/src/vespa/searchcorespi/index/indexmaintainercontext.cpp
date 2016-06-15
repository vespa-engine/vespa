// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".searchcorespi.index.indexmaintainercontext");

#include "indexmaintainercontext.h"

using search::common::FileHeaderContext;
using search::TuneFileAttributes;
using searchcorespi::IIndexManager;

namespace searchcorespi {
namespace index {

IndexMaintainerContext::IndexMaintainerContext(IThreadingService &threadingService,
                                               IIndexManager::Reconfigurer &reconfigurer,
                                               const FileHeaderContext &fileHeaderContext,
                                               vespalib::ThreadExecutor & warmupExecutor)
    : _threadingService(threadingService),
      _reconfigurer(reconfigurer),
      _fileHeaderContext(fileHeaderContext),
      _warmupExecutor(warmupExecutor)
{
}

} // namespace index
} // namespace searchcorespi

