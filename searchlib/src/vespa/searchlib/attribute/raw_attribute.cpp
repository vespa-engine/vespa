// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "raw_attribute.h"
#include <cassert>

namespace search::attribute {

RawAttribute::RawAttribute(const vespalib::string& name, const Config& config)
    : NotImplementedAttribute(name, config)
{
}

RawAttribute::~RawAttribute() = default;

namespace {

template <bool desc>
unsigned char remap(unsigned char val)
{
    return (desc ? (0xff - val) : val);
}

template <bool desc>
long serialize_for_sort(vespalib::ConstArrayRef<char> raw, void* serTo, long available)
{
    auto src = reinterpret_cast<const unsigned char *>(raw.data());
    auto src_end = src + raw.size();
    size_t extra = 1;
    for (auto p = src; p != src_end; ++p) {
        if (*p >= 0xfe) {
            ++extra;
        }
    }
    if (available >= (long)(raw.size() + extra)) {
        auto dst = static_cast<unsigned char *>(serTo);
        auto dst_orig = dst;
        for (auto p = src; p != src_end; ++p) {
            if (*p >= 0xfe) {
                *dst++ = remap<desc>(0xff);
                *dst++ = remap<desc>(*p);
            } else {
                *dst++ = remap<desc>(*p + 1);
            }
        }
        *dst++ = remap<desc>(0);
        assert(raw.size() + extra + dst_orig == dst);
    } else {
        return -1;
    }
    return raw.size() + extra;
}

}

long
RawAttribute::onSerializeForAscendingSort(DocId doc, void* serTo, long available, const common::BlobConverter*) const
{
    auto raw = get_raw(doc);
    return serialize_for_sort<false>(raw, serTo, available);
}

long
RawAttribute::onSerializeForDescendingSort(DocId doc, void* serTo, long available, const common::BlobConverter*) const
{
    auto raw = get_raw(doc);
    return serialize_for_sort<true>(raw, serTo, available);
}

}
