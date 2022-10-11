// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "compaction_context.h"
#include "compacting_buffers.h"
#include "i_compactable.h"

namespace vespalib::datastore {

CompactionContext::CompactionContext(ICompactable& store,
                                     std::unique_ptr<CompactingBuffers> compacting_buffers)
    : _store(store),
      _compacting_buffers(std::move(compacting_buffers)),
      _filter(_compacting_buffers->make_entry_ref_filter())
{
}

CompactionContext::~CompactionContext()
{
    _compacting_buffers->finish();
}

void
CompactionContext::compact(vespalib::ArrayRef<AtomicEntryRef> refs)
{
    for (auto &atomic_entry_ref : refs) {
        auto ref = atomic_entry_ref.load_relaxed();
        if (ref.valid() && _filter.has(ref)) {
            EntryRef newRef = _store.move_on_compact(ref);
            atomic_entry_ref.store_release(newRef);
        }
    }
}

}
