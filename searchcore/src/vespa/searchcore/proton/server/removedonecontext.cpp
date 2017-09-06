// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "removedonecontext.h"
#include "removedonetask.h"
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/searchcore/proton/reference/i_gid_to_lid_change_handler.h>

namespace proton {

RemoveDoneContext::RemoveDoneContext(std::unique_ptr<FeedToken> token,
                                     const FeedOperation::Type opType,
                                     PerDocTypeFeedMetrics &metrics,
                                     vespalib::Executor &executor,
                                     IDocumentMetaStore &documentMetaStore,
                                     IGidToLidChangeHandler &gidToLidChangeHandler,
                                     const document::GlobalId &gid,
                                     uint32_t lid,
                                     search::SerialNum serialNum,
                                     bool enableNotifyRemoveDone)
    : OperationDoneContext(std::move(token), opType, metrics),
      _executor(executor),
      _task(),
      _gidToLidChangeHandler(gidToLidChangeHandler),
      _gid(gid),
      _serialNum(serialNum),
      _enableNotifyRemoveDone(enableNotifyRemoveDone)
{
    if (lid != 0) {
        _task = std::make_unique<RemoveDoneTask>(documentMetaStore, lid);
    }
}

RemoveDoneContext::~RemoveDoneContext()
{
    if (_enableNotifyRemoveDone) {
        _gidToLidChangeHandler.notifyRemoveDone(_gid, _serialNum);
    }
    ack();
    if (_task) {
        vespalib::Executor::Task::UP res = _executor.execute(std::move(_task));
        assert(!res);
    }
}

}  // namespace proton
