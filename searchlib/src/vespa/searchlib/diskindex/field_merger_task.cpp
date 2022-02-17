// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_merger_task.h"
#include "field_merger.h"
#include "field_mergers_state.h"

namespace search::diskindex {

void
FieldMergerTask::run()
{
    _field_merger.process_merge_field();
    if (_field_merger.failed()) {
        _field_mergers_state.field_merger_done(_field_merger, true);
    } else if (_field_merger.done()) {
        _field_mergers_state.field_merger_done(_field_merger, false);
    } else {
        _field_mergers_state.schedule_task(_field_merger);
    }
}

}
