// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "atomic_entry_ref.h"
#include <vespa/vespalib/util/arrayref.h>
#include <memory>

namespace vespalib::datastore {

/**
 * A compaction context is used when performing a compaction of data buffers in a data store.
 *
 * All entry refs pointing to allocated data in the store must be passed to the compaction context
 * such that these can be updated according to the buffer compaction that happens internally.
 */
struct ICompactionContext {
    using UP = std::unique_ptr<ICompactionContext>;
    virtual ~ICompactionContext() {}
    virtual void compact(vespalib::ArrayRef<AtomicEntryRef> refs) = 0;
};

}
