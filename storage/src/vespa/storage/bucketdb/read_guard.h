// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "const_iterator.h"
#include <vespa/document/bucket/bucketid.h>
#include <functional>
#include <memory>
#include <vector>

namespace storage::bucketdb {


/*
 * Read guard for accessing the bucket tree of an underlying bucket database
 * in a thread-safe, read-only manner.
 *
 * Important: If the underlying database is _not_ backed by a B-tree, the
 * read guard does _not_ provide a stable view of the bucket key set when
 * iterating, as that is not possible without locking the entire DB.
 *
 * If the guard is created by a B-tree DB, the following properties hold:
 *   - The set of bucket keys that can be iterated over is stable for the lifetime
 *     of the read guard.
 *   - The bucket _values_ may change during the lifetime of the read guard,
 *     but the reader will always observe a fully consistent value as if it were
 *     written atomically.
 *
 * Do not hold read guards for longer than absolutely necessary, as they cause
 * memory to be retained by the backing DB until released.
 */

template <typename ValueT, typename ConstRefT = const ValueT&>
class ReadGuard {
public:
    ReadGuard() = default;
    virtual ~ReadGuard() = default;

    ReadGuard(ReadGuard&&) = delete;
    ReadGuard& operator=(ReadGuard&&) = delete;
    ReadGuard(const ReadGuard&) = delete;
    ReadGuard& operator=(const ReadGuard&) = delete;

    virtual std::vector<ValueT> find_parents_and_self(const document::BucketId& bucket) const = 0;
    virtual std::vector<ValueT> find_parents_self_and_children(const document::BucketId& bucket) const = 0;
    virtual void for_each(std::function<void(uint64_t, const ValueT&)> func) const = 0;
    virtual std::unique_ptr<ConstIterator<ConstRefT>> create_iterator() const = 0;
    // If the underlying guard represents a snapshot, returns its monotonically
    // increasing generation. Otherwise returns 0.
    [[nodiscard]] virtual uint64_t generation() const noexcept = 0;
};

}
