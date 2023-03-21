// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "raw_attribute.h"

namespace search::attribute {

RawAttribute::RawAttribute(const vespalib::string& name, const Config& config)
    : NotImplementedAttribute(name, config)
{
}

RawAttribute::~RawAttribute() = default;

long
RawAttribute::onSerializeForAscendingSort(DocId doc, void* serTo, long available, const common::BlobConverter*) const
{
    auto raw = get_raw(doc);
    if (available >= (long)raw.size()) {
        memcpy(serTo, raw.data(), raw.size());
    } else {
        return -1;
    }
    return raw.size();
}

long
RawAttribute::onSerializeForDescendingSort(DocId doc, void* serTo, long available, const common::BlobConverter*) const
{
    auto raw = get_raw(doc);
    if (available >= (long)raw.size()) {
        auto *dst = static_cast<unsigned char *>(serTo);
        const auto * src(reinterpret_cast<const uint8_t *>(raw.data()));
        for (size_t i(0); i < raw.size(); ++i) {
            dst[i] = 0xff - src[i];
        }
    } else {
        return -1;
    }
    return raw.size();
}

}
