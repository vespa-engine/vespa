// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "removedonecontext.h"
#include "removedonetask.h"
#include <vespa/searchcore/proton/common/feedtoken.h>

namespace proton {

RemoveDoneContext::RemoveDoneContext(std::unique_ptr<FeedToken> token,
                                     const FeedOperation::Type opType,
                                     PerDocTypeFeedMetrics &metrics,
                                     vespalib::Executor &executor,
                                     IDocumentMetaStore &documentMetaStore,
                                     uint32_t lid)
    : OperationDoneContext(std::move(token), opType, metrics),
      _executor(executor),
      _task()
{
    if (lid != 0) {
        _task = std::make_unique<RemoveDoneTask>(documentMetaStore, lid);
    }
}

RemoveDoneContext::~RemoveDoneContext()
{
    ack();
    if (_task) {
        vespalib::Executor::Task::UP res = _executor.execute(std::move(_task));
        assert(!res);
    }
}

}  // namespace proton
