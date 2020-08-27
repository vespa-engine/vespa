// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/idestructorcallback.h>
#include <memory>
#include <cstdint>
#include <vector>

namespace vespalib { class Executor; }

namespace proton {

class ForceCommitDoneTask;
struct IDocumentMetaStore;
class DocIdLimit;

/**
 * Context class for forced commits that schedules a task when
 * instance is destroyed. Typically a shared pointer to an instance is
 * passed around to multiple worker threads that performs portions of
 * a larger task before dropping the shared pointer, triggering the
 * callback when all worker threads have completed.
 */
class ForceCommitContext : public search::IDestructorCallback
{
    vespalib::Executor &_executor;
    std::unique_ptr<ForceCommitDoneTask> _task;
    uint32_t    _committedDocIdLimit;
    DocIdLimit *_docIdLimit;

public:
    ForceCommitContext(vespalib::Executor &executor,
                       IDocumentMetaStore &documentMetaStore);

    ~ForceCommitContext() override;

    void reuseLids(std::vector<uint32_t> &&lids);
    void holdUnblockShrinkLidSpace();
    void registerCommittedDocIdLimit(uint32_t committedDocIdLimit, DocIdLimit *docIdLimit);
};

}  // namespace proton
