// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "raw_attribute.h"
#include <vespa/searchcommon/attribute/i_sort_blob_writer.h>
#include <cassert>

namespace search::attribute {

RawAttribute::RawAttribute(const std::string& name, const Config& config)
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

/*
 * Serialize raw data to a sort blob that can be passed to memcmp.
 *
 * End of raw data is encoded as 0, while a bias of 1 is added to raw data byte values to
 * differentiate from end of raw data. To avoid wraparound, 0xfe and 0xff are encoded
 * as two bytes (0xfe => [0xff, 0xfe] and 0xff => [0xff, 0xff]).
 *
 * If sort order is descending, all encoded values are inverted, this
 * is done by remap function above.
 */
template <bool desc>
long serialize_for_sort(std::span<const char> raw, void* serTo, long available)
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

template <bool desc>
class RawAttributeSortBlobWriter : public ISortBlobWriter {
private:
    const RawAttribute& _attr;
public:
    RawAttributeSortBlobWriter(const RawAttribute& attr) noexcept : _attr(attr) {}
    long write(uint32_t docid, void* buf, long available) override {
        auto raw = _attr.get_raw(docid);
        return serialize_for_sort<desc>(raw, buf, available);
    }
};

}

bool
RawAttribute::is_sortable() const noexcept
{
    return true;
}

std::unique_ptr<ISortBlobWriter>
RawAttribute::make_sort_blob_writer(bool ascending, const common::BlobConverter*,
                                    common::sortspec::MissingPolicy,
                                    std::string_view) const
{
    if (ascending) {
        // Note: Template argument for the writer is "descending".
        return std::make_unique<RawAttributeSortBlobWriter<false>>(*this);
    } else {
        return std::make_unique<RawAttributeSortBlobWriter<true>>(*this);
    }
}

}
