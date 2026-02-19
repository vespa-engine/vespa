// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "entry_ref_filter.h"
#include "i_compaction_context.h"

namespace vespalib::datastore {

class CompactingBuffers;
struct ICompactable;

/**
 * A compaction context is used when performing a compaction of data buffers in a data store.
 */
class CompactionContext : public ICompactionContext {
private:
    ICompactable&                                           _store;
    std::unique_ptr<vespalib::datastore::CompactingBuffers> _compacting_buffers;
    EntryRefFilter                                          _filter;

public:
    CompactionContext(ICompactable& store, std::unique_ptr<CompactingBuffers> compacting_buffers);
    ~CompactionContext() override;
    void compact(std::span<AtomicEntryRef> refs) override;
};

} // namespace vespalib::datastore
