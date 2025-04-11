// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/sortspec.h>
#include <optional>

namespace search::attribute {

/*
 * Class for writing numeric sort blobs for arrays and
 * weighted sets of type T with ascending or descending
 * sort order.
 */
template <typename T, bool asc>
class NumericSortBlobWriter {
    std::optional<T>             _best;
    std::vector<unsigned char>   _missing_blob; // blob to emit when not having a value
    std::optional<unsigned char> _value_prefix; // optional prefix to emit when having a value

    size_t value_prefix_len() const noexcept { return _value_prefix.has_value() ? 1 : 0; }
    void set_missing_blob(T value);
public:
    NumericSortBlobWriter(search::common::sortspec::MissingPolicy policy, T missing_value, bool multi_value) noexcept;
    ~NumericSortBlobWriter() noexcept;
    void candidate(T val);
    void reset();
    long write(void *serTo, size_t available);
};

}
