// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sort_blob_writer.h"
#include <optional>

namespace search::common { class BlobConverter; }

namespace search::attribute {

/*
 * Class for writing numeric sort blobs for arrays and
 * weighted sets of string with ascending or descending
 * sort order.
 */
class StringSortBlobWriter : public SortBlobWriter {
    using BlobConverter = common::BlobConverter;
    std::optional<size_t> _best_size;
    unsigned char*        _serialize_to;
    size_t                _available;
    const BlobConverter*  _bc;
    const bool            _asc;
public:
    StringSortBlobWriter(void* serialize_to, size_t available, const BlobConverter* bc, bool asc) noexcept;
    ~StringSortBlobWriter() noexcept;
    bool candidate(const char* val);
    long write();
};

}
