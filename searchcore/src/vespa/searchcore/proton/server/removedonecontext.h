// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "operationdonecontext.h"
#include <vespa/vespalib/util/executor.h>
#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/searchcore/proton/reference/pending_notify_remove_done.h>

namespace proton {

struct IDocumentMetaStore;


/**
 * Context class for document removes that acks remove andschedules a
 * task when instance is destroyed. Typically a shared pointer to an
 * instance is passed around to multiple worker threads that performs
 * portions of a larger task before dropping the shared pointer,
 * triggering the ack and task scheduling when all worker threads have
 * completed.
 */
class RemoveDoneContext : public OperationDoneContext
{
    vespalib::Executor &_executor;
    std::unique_ptr<vespalib::Executor::Task> _task;
    PendingNotifyRemoveDone _pendingNotifyRemoveDone;

public:
    RemoveDoneContext(FeedToken token, vespalib::Executor &executor, IDocumentMetaStore &documentMetaStore,
                      PendingNotifyRemoveDone &&pendingNotifyRemoveDone, uint32_t lid);
    ~RemoveDoneContext() override;
};


}  // namespace proton
