// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/index/ithreadingservice.h>
#include <vespa/vespalib/util/lambdatask.h>

namespace proton::test {

/**
 * Run the given function in the master thread and wait until done.
 */
template <typename FunctionType>
void
runInMasterAndSync(searchcorespi::index::IThreadingService &writeService, FunctionType func)
{
    writeService.master().execute(vespalib::makeLambdaTask(std::move(func)));
    writeService.master().sync();
}

/**
 * Run the given function in the master thread.
 */
template <typename FunctionType>
void
runInMaster(searchcorespi::index::IThreadingService &writeService, FunctionType func)
{
    writeService.master().execute(vespalib::makeLambdaTask(std::move(func)));
}

}
