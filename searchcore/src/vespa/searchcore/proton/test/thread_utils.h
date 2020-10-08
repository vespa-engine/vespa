// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/index/ithreadingservice.h>
#include <vespa/vespalib/util/closuretask.h>

namespace proton::test {

template <typename FunctionType>
void
runFunction(FunctionType *func)
{
    (*func)();
}

/**
 * Run the given function in the master thread and wait until done.
 */
template <typename FunctionType>
void
runInMaster(searchcorespi::index::IThreadingService &writeService, FunctionType func)
{
    writeService.master().execute(vespalib::makeTask
            (vespalib::makeClosure(&runFunction<FunctionType>, &func)));
    writeService.sync();
}

}
