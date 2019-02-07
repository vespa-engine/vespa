// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/executor.h>
#include <vector>

namespace proton {

struct IDocumentMetaStore;

/**
 * Class for task to be executed when a forced commit has completed and
 * memory index and attributes have been updated.
 *
 * The task handles two things:
 *
 * 1.  Passing on lids that can be reused do document meta store.
 *     They have to go through a hold cycle in order for searches that
 *     might have posting lists referencing the lids in context of
 *     their old identity.
 *
 * 2.  Shrinking of document meta store lid space.  This also goes through
 *     a hold cycle, since it must be handled after any lids to be reused.
 */
class ForceCommitDoneTask : public vespalib::Executor::Task
{
    std::vector<uint32_t> _lidsToReuse;
    bool _holdUnblockShrinkLidSpace;
    IDocumentMetaStore &_documentMetaStore;

public:
    ForceCommitDoneTask(IDocumentMetaStore &documentMetaStore);

    ~ForceCommitDoneTask() override;

    void reuseLids(std::vector<uint32_t> &&lids);

    void holdUnblockShrinkLidSpace() {
        _holdUnblockShrinkLidSpace = true;
    }

    void run() override;

    bool empty() const {
        return _lidsToReuse.empty() && !_holdUnblockShrinkLidSpace;
    }
};

}  // namespace proton
