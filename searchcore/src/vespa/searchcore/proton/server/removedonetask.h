// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/executor.h>

namespace proton
{

struct IDocumentMetaStore;

/**
 * Class for task to be executed when a document remove completed and
 * memory index and attributes have been updated.
 *
 * The task handles one thing:
 *
 * 1.  Passing on lid that can be reused do document meta store.
 *     It have to go through a hold cycle in order for searches that
 *     might have posting lists referencing the lid in context of
 *     its old identity.
 *
 */
class RemoveDoneTask : public vespalib::Executor::Task
{
    IDocumentMetaStore &_documentMetaStore;
    // lid to reuse, can be 0 if reuse was handled by lid reuse delayer
    uint32_t _lid;


public:
    RemoveDoneTask(IDocumentMetaStore &documentMetaStore, uint32_t lid);
    ~RemoveDoneTask() override;

    void run() override;
};


}  // namespace proton
