// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.index.diskindexwrapper");

#include "diskindexwrapper.h"

using search::TuneFileSearch;

namespace proton {

DiskIndexWrapper::DiskIndexWrapper(const vespalib::string &indexDir,
                                   const TuneFileSearch &tuneFileSearch,
                                   size_t cacheSize)
    : _index(indexDir, cacheSize)
{
    bool setupIndexOk = _index.setup(tuneFileSearch);
    assert(setupIndexOk);
    (void) setupIndexOk;
}

DiskIndexWrapper::DiskIndexWrapper(const DiskIndexWrapper &oldIndex,
                                   const TuneFileSearch &tuneFileSearch,
                                   size_t cacheSize)
    : _index(oldIndex._index.getIndexDir(), cacheSize)
{
    bool setupIndexOk = _index.setup(tuneFileSearch, oldIndex._index);
    assert(setupIndexOk);
    (void) setupIndexOk;
}

}  // namespace proton

