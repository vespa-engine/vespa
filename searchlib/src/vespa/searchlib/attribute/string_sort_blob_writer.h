// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/sortspec.h>
#include <optional>

namespace search::common { class BlobConverter; }

namespace search::attribute {

/*
 * Class for writing numeric sort blobs for arrays and
 * weighted sets of string with ascending or descending
 * sort order.
 */
template <bool asc>
class StringSortBlobWriter {
    using BlobConverter = common::BlobConverter;
    std::optional<size_t>        _best_size;
    unsigned char*               _serialize_to;
    size_t                       _available;
    const BlobConverter*         _bc;
    std::vector<unsigned char>   _missing_blob; // blob to emit when not having a value
    std::optional<unsigned char> _value_prefix; // optional prefix to emit when having a value

    size_t value_prefix_len() const noexcept { return _value_prefix.has_value() ? 1 : 0; }
    void set_missing_blob(std::string_view value);
public:
    StringSortBlobWriter(const BlobConverter* bc, search::common::sortspec::MissingPolicy policy,
                         std::string_view missing_value, bool multi_value) noexcept;
    ~StringSortBlobWriter() noexcept;
    bool candidate(const char* val);
    void reset(void* serialize_to, size_t available);
    long write();
};

}
