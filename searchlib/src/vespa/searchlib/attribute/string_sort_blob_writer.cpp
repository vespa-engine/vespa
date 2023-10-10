// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_sort_blob_writer.h"
#include <vespa/searchcommon/common/iblobconverter.h>
#include <vespa/vespalib/util/arrayref.h>
#include <algorithm>
#include <cstring>

namespace search::attribute {

StringSortBlobWriter::StringSortBlobWriter(void* serialize_to, size_t available, const BlobConverter* bc, bool asc) noexcept
    : _best_size(),
      _serialize_to(static_cast<unsigned char*>(serialize_to)),
      _available(available),
      _bc(bc),
      _asc(asc)
{
}

StringSortBlobWriter::~StringSortBlobWriter() noexcept = default;

bool
StringSortBlobWriter::candidate(const char* val)
{
    size_t size = std::strlen(val) + 1;
    vespalib::ConstBufferRef buf(val, size);
    if (_bc != nullptr) {
        buf = _bc->convert(buf);
    }
    if (_best_size.has_value()) {
        auto common_size = std::min(_best_size.value(), buf.size());
        auto cmpres = std::memcmp(_serialize_to + 1, buf.data(), common_size);
        if (_asc) {
            if (cmpres < 0 || (cmpres == 0 && _best_size.value() < buf.size())) {
                return true;
            }
        } else {
            if (cmpres > 0 || (cmpres == 0 && _best_size.value() > buf.size())) {
                return true;
            }
        }
    }
    if (_available < buf.size() + 1) {
        return false;
    }
    _serialize_to[0] = has_value;
    memcpy(_serialize_to + 1, buf.data(), buf.size());
    _best_size = buf.size();
    return true;
}

long
StringSortBlobWriter::write()
{
    if (_best_size.has_value()) {
        if (!_asc) {
            vespalib::ArrayRef<unsigned char> buf(_serialize_to + 1, _best_size.value());
            for (auto& c : buf) {
                c = 0xff - c;
            }
        }
        return 1 + _best_size.value();
    } else {
        if (_available < 1) {
            return -1;
        }
        _serialize_to[0] = missing_value;
        return 1;
    }
}

}
