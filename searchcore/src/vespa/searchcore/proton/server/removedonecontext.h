// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "operationdonecontext.h"
#include <vespa/searchcore/proton/common/ipendinglidtracker.h>
#include <vespa/vespalib/util/executor.h>
#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/common/serialnum.h>

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
    IPendingLidTracker::Token _uncommitted;

public:
    RemoveDoneContext(FeedToken token, IPendingLidTracker::Token uncommitted, vespalib::Executor &executor, IDocumentMetaStore &documentMetaStore,
                      uint32_t lid);
    ~RemoveDoneContext() override;
};


}  // namespace proton
