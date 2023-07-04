// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sort_blob_writer.h"
#include <optional>

namespace search::attribute {

/*
 * Class for writing numeric sort blobs for arrays and
 * weighted sets of type T with ascending or descending
 * sort order.
 */
template <typename T, bool asc>
class NumericSortBlobWriter : public SortBlobWriter {
    std::optional<T> _best;
public:
    NumericSortBlobWriter() noexcept;
    ~NumericSortBlobWriter() noexcept;
    void candidate(T val);
    long write(void *serTo, size_t available);
};

}
