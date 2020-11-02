// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>

namespace storage::bucketdb {

/**
 * Iterator interface for iterating over entries in a bucket database.
 * Implementations shall guarantee that iteration happens in natural key order.
 *
 * Key type is in the standard u64 "reversed bucket ID bits" form.
 * Value type is always a (possibly wrapped) const reference to the entry itself.
 */
template <typename ConstRefT>
class ConstIterator {
public:
    virtual ~ConstIterator() = default;
    virtual void next() noexcept = 0;
    [[nodiscard]] virtual bool valid() const noexcept = 0;
    [[nodiscard]] virtual uint64_t key() const noexcept = 0;
    [[nodiscard]] virtual ConstRefT value() const = 0;
};

}
