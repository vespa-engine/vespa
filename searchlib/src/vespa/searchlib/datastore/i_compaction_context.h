// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/array.h>

namespace search {
namespace datastore {

/**
 * A compaction context is used when performing a compaction of data buffers in a data store.
 *
 * All entry refs pointing to allocated data in the store must be passed to the compaction context
 * such that these can be updated according to the buffer compaction that happens internally.
 */
struct ICompactionContext {
    using UP = std::unique_ptr<ICompactionContext>;
    virtual ~ICompactionContext() {}
    virtual void compact(vespalib::ArrayRef<EntryRef> refs) = 0;
};

}
}
