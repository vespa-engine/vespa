// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_sort_blob_writer.h"
#include <vespa/searchcommon/common/iblobconverter.h>
#include <algorithm>
#include <cstring>
#include <span>

using search::common::sortspec::MissingPolicy;

namespace search::attribute {

namespace {

template <bool asc>
unsigned char remap(unsigned char val)
{
    return (asc ? val : (0xff - val));
}

}

template <bool asc>
StringSortBlobWriter<asc>::StringSortBlobWriter(const BlobConverter* bc, MissingPolicy policy,
                                                std::string_view missing_value, bool multi_value) noexcept
    : _best_size(),
      _serialize_to(nullptr),
      _available(0),
      _bc(bc),
      _missing_blob(),
      _value_prefix()
{
    switch (policy) {
        case MissingPolicy::DEFAULT:
            if (multi_value) {
                _missing_blob.emplace_back(1);
                _value_prefix.emplace(0);
            } else {
                set_missing_blob({}); // Serialize missing value as empty string
            }
            break;
        case MissingPolicy::FIRST:
            _missing_blob.emplace_back(0);
            _value_prefix.emplace(1);
            break;
        case MissingPolicy::LAST:
            _missing_blob.emplace_back(1);
            _value_prefix.emplace(0);
            break;
        case MissingPolicy::AS:
            set_missing_blob(missing_value);
            break;
        default:
            break;
    }
}

template <bool asc>
StringSortBlobWriter<asc>::~StringSortBlobWriter() noexcept = default;

template <bool asc>
void
StringSortBlobWriter<asc>::set_missing_blob(std::string_view value)
{
    _missing_blob.clear();
    for (auto c: value) {
        _missing_blob.emplace_back(remap<asc>(c));
    }
    _missing_blob.emplace_back(remap<asc>(0));
}

template <bool asc>
void
StringSortBlobWriter<asc>::reset(void *serialize_to, size_t available)
{
    _serialize_to = static_cast<unsigned char*>(serialize_to);
    _available = available;
    _best_size.reset();
}

template <bool asc>
bool
StringSortBlobWriter<asc>::candidate(const char* val)
{
    size_t size = std::strlen(val) + 1;
    vespalib::ConstBufferRef buf(val, size);
    if (_bc != nullptr) {
        buf = _bc->convert(buf);
    }
    if (_best_size.has_value()) {
        auto common_size = std::min(_best_size.value(), buf.size());
        auto cmpres = std::memcmp(_serialize_to + value_prefix_len(), buf.data(), common_size);
        if constexpr (asc) {
            if (cmpres < 0 || (cmpres == 0 && _best_size.value() < buf.size())) {
                return true;
            }
        } else {
            if (cmpres > 0 || (cmpres == 0 && _best_size.value() > buf.size())) {
                return true;
            }
        }
    }
    if (_available < buf.size() + value_prefix_len()) {
        return false;
    }
    if (_value_prefix.has_value()) {
        _serialize_to[0] = _value_prefix.value();
    }
    memcpy(_serialize_to + value_prefix_len(), buf.data(), buf.size());
    _best_size = buf.size();
    return true;
}

template <bool asc>
long
StringSortBlobWriter<asc>::write()
{
    if (_best_size.has_value()) {
        if constexpr (!asc) {
            std::span<unsigned char> buf(_serialize_to + value_prefix_len(), _best_size.value());
            for (auto& c : buf) {
                c = 0xff - c;
            }
        }
        return value_prefix_len() + _best_size.value();
    } else {
        if (_available < _missing_blob.size()) {
            return -1;
        }
        memcpy(_serialize_to, _missing_blob.data(), _missing_blob.size());
        return _missing_blob.size();
    }
}

template class StringSortBlobWriter<false>;
template class StringSortBlobWriter<true>;

}
