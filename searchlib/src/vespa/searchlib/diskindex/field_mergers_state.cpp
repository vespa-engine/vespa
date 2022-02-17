// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_mergers_state.h"
#include "field_merger.h"
#include "field_merger_task.h"
#include "fusion_output_index.h"
#include <vespa/searchcommon/common/schema.h>
#include <vespa/vespalib/util/cpu_usage.h>
#include <vespa/vespalib/util/executor.h>
#include <cassert>

using vespalib::CpuUsage;

namespace search::diskindex {

FieldMergersState::FieldMergersState(const FusionOutputIndex& fusion_out_index, vespalib::Executor& executor, std::shared_ptr<IFlushToken> flush_token)
    : _fusion_out_index(fusion_out_index),
      _executor(executor),
      _flush_token(std::move(flush_token)),
      _done(_fusion_out_index.get_schema().getNumIndexFields()),
      _failed(0u),
      _field_mergers(_fusion_out_index.get_schema().getNumIndexFields())
{
}

FieldMergersState::~FieldMergersState()
{
    wait_field_mergers_done();
}

FieldMerger&
FieldMergersState::alloc_field_merger(uint32_t id)
{
    assert(id < _field_mergers.size());
    auto field_merger = std::make_unique<FieldMerger>(id, _fusion_out_index, _flush_token);
    auto& result = *field_merger;
    assert(!_field_mergers[id]);
    _field_mergers[id] = std::move(field_merger);
    return result;
}

void
FieldMergersState::destroy_field_merger(FieldMerger& field_merger)
{
    uint32_t id = field_merger.get_id();
    assert(id < _field_mergers.size());
    std::unique_ptr<FieldMerger> old_merger;
    old_merger = std::move(_field_mergers[id]);
    assert(old_merger.get() == &field_merger);
    old_merger.reset();
    _done.countDown();
}

void
FieldMergersState::field_merger_done(FieldMerger& field_merger, bool failed)
{
    if (failed) {
        ++_failed;
    }
    destroy_field_merger(field_merger);
}

void
FieldMergersState::wait_field_mergers_done()
{
    _done.await();
}

void
FieldMergersState::schedule_task(FieldMerger& field_merger)
{
    auto task = std::make_unique<FieldMergerTask>(field_merger, *this);
    auto rejected = _executor.execute(CpuUsage::wrap(std::move(task), CpuUsage::Category::COMPACT));
    assert(!rejected);
}

}
