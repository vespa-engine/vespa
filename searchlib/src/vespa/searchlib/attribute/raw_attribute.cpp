// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "raw_attribute.h"
#include <vespa/searchcommon/attribute/i_sort_blob_writer.h>
#include <vespa/vespalib/encoding/base64.h>
#include <vespa/vespalib/util/exceptions.h>
#include <cassert>
#include <cstring>

using search::common::sortspec::MissingPolicy;
using vespalib::Base64;
using vespalib::IllegalArgumentException;

namespace search::attribute {

RawAttribute::RawAttribute(const std::string& name, const Config& config)
    : NotImplementedAttribute(name, config)
{
}

RawAttribute::~RawAttribute() = default;

namespace {

template <bool asc>
unsigned char remap(unsigned char val)
{
    return (asc ? val : (0xff - val));
}

size_t calc_serialized_for_sort_len(std::span<const char> raw) {
    size_t extra = 1;
    for (unsigned char c : raw) {
        if (c >= 0xfe) {
            ++extra;
        }
    }
    return raw.size() + extra;
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
template <bool asc>
size_t serialize_for_sort(std::span<const char> raw, void* serTo)
{
    auto dst = static_cast<unsigned char *>(serTo);
    auto dst_orig = dst;
    for (unsigned char c : raw) {
        if (c >= 0xfe) {
            *dst++ = remap<asc>(0xff);
            *dst++ = remap<asc>(c);
        } else {
            *dst++ = remap<asc>(c + 1);
        }
    }
    *dst++ = remap<asc>(0);
    return dst - dst_orig;
}

template <bool asc>
class RawAttributeSortBlobWriter : public ISortBlobWriter {
private:
    const RawAttribute& _attr;
    std::vector<unsigned char>   _missing_blob; // blob to emit when not having a value
    std::optional<unsigned char> _value_prefix; // optional prefix to emit when having a value
    size_t value_prefix_len() const noexcept { return _value_prefix.has_value() ? 1 : 0; }
    void set_missing_blob(std::span<const char> value);
public:
    RawAttributeSortBlobWriter(const RawAttribute& attr, MissingPolicy policy,
                               std::span<const char> missing_value) noexcept;
    ~RawAttributeSortBlobWriter() override;
    long write(uint32_t docid, void* buf, long available) override;
};

template<bool asc>
RawAttributeSortBlobWriter<asc>::RawAttributeSortBlobWriter(const RawAttribute &attr,
                                                            MissingPolicy policy,
                                                            std::span<const char> missing_value) noexcept
    : _attr(attr),
      _missing_blob(),
      _value_prefix()
{
    switch (policy) {
        case MissingPolicy::DEFAULT:
            set_missing_blob({}); // Serialize missing value as undefined value, i.e. empty value
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
RawAttributeSortBlobWriter<asc>::~RawAttributeSortBlobWriter() = default;

template <bool asc>
void
RawAttributeSortBlobWriter<asc>::set_missing_blob(std::span<const char> value)
{
    auto serialized_len = calc_serialized_for_sort_len(value);
    _missing_blob.clear();
    _missing_blob.resize(serialized_len);
    auto ret = serialize_for_sort<asc>(value, _missing_blob.data());
    assert(ret == serialized_len);
}

template <bool asc>
long
RawAttributeSortBlobWriter<asc>::write(uint32_t docid, void* buf, long available)
{
    auto raw = _attr.get_raw(docid);
    if (!raw.empty()) {
        auto vp_len = value_prefix_len();
        auto serialized_len = calc_serialized_for_sort_len(raw);
        if (available >= (long)(vp_len + serialized_len)) {
            auto dst = static_cast<unsigned char *>(buf);
            if (_value_prefix.has_value()) {
                dst[0] = _value_prefix.value();
            }
            auto ret = serialize_for_sort<asc>(raw, dst + vp_len);
            assert(ret == serialized_len);
            return ret + vp_len;
        } else {
            return -1;
        }
    } else {
        if (available >= (long) _missing_blob.size()) {
            memcpy(buf, _missing_blob.data(), _missing_blob.size());
            return _missing_blob.size();
        } else {
            return -1;
        }
    }
}

}

bool
RawAttribute::is_sortable() const noexcept
{
    return true;
}

std::unique_ptr<ISortBlobWriter>
RawAttribute::make_sort_blob_writer(bool ascending, const common::BlobConverter*,
                                    common::sortspec::MissingPolicy policy,
                                    std::string_view missing_value) const
{
    std::vector<char> raw_missing_value;
    if (policy == MissingPolicy::AS) {
        try {
            auto raw_string = Base64::decode(missing_value.data(), missing_value.size());
            raw_missing_value.assign(raw_string.begin(), raw_string.end());
        } catch (const IllegalArgumentException& e) {
            throw IllegalArgumentException("Failed converting string '" + std::string(missing_value) + "' to a raw value: " + e.getMessage());
        }
    }
    if (ascending) {
        return std::make_unique<RawAttributeSortBlobWriter<true>>(*this, policy, raw_missing_value);
    } else {
        return std::make_unique<RawAttributeSortBlobWriter<false>>(*this, policy, raw_missing_value);
    }
}

}
