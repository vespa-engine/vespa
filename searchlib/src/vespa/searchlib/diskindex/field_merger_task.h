// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/threadexecutor.h>

namespace search::diskindex {

class FieldMerger;
class FieldMergersState;

/*
 * Task for processing a portion of a field merge.
 */
class FieldMergerTask : public vespalib::Executor::Task
{
    FieldMerger&      _field_merger;
    FieldMergersState& _field_mergers_state;

    void run() override;
public:
    FieldMergerTask(FieldMerger& field_merger, FieldMergersState& field_mergers_state)
        : vespalib::Executor::Task(),
          _field_merger(field_merger),
          _field_mergers_state(field_mergers_state)
    {
    }
};

}
