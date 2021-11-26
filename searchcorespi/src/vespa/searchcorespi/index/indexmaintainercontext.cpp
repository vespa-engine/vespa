// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexmaintainercontext.h"

using search::common::FileHeaderContext;
using search::TuneFileAttributes;
using searchcorespi::IIndexManager;

namespace searchcorespi::index {

IndexMaintainerContext::IndexMaintainerContext(IThreadingService &threadingService,
                                               IIndexManager::Reconfigurer &reconfigurer,
                                               const FileHeaderContext &fileHeaderContext,
                                               vespalib::Executor & warmupExecutor)
    : _threadingService(threadingService),
      _reconfigurer(reconfigurer),
      _fileHeaderContext(fileHeaderContext),
      _warmupExecutor(warmupExecutor)
{
}

}
