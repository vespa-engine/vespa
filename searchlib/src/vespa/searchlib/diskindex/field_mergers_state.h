// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/util/queue.h>
#include <vespa/vespalib/util/count_down_latch.h>
#include <atomic>

namespace search { class IFlushToken; }
namespace vespalib { class ThreadExecutor; }

namespace search::diskindex {

class FieldMerger;
class FusionOutputIndex;

/*
 * This class has ownership of active field mergers until they are
 * done or failed.
 */
class FieldMergersState {
    const FusionOutputIndex&                  _fusion_out_index;
    vespalib::ThreadExecutor&                 _executor;
    std::shared_ptr<IFlushToken>              _flush_token;
    document::Semaphore                       _concurrent;
    vespalib::CountDownLatch                  _done;
    std::atomic<uint32_t>                     _failed;
    std::vector<std::unique_ptr<FieldMerger>> _field_mergers;

    void destroy_field_merger(FieldMerger& field_merger);
public:
    FieldMergersState(const FusionOutputIndex& fusion_out_index, vespalib::ThreadExecutor& executor, std::shared_ptr<IFlushToken> flush_token);
    ~FieldMergersState();
    FieldMerger& alloc_field_merger(uint32_t id);
    void field_merger_done(FieldMerger& field_merger, bool failed);
    void wait_field_mergers_done();
    void schedule_task(FieldMerger& field_merger);
    uint32_t get_failed() const noexcept { return _failed; }
};

}
