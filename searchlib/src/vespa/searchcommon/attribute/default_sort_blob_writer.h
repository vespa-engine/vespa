// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_sort_blob_writer.h"

namespace search::common { class BlobConverter; }

namespace search::attribute {

class IAttributeVector;

/**
 * Writer for sort blobs that uses the IAttributeVector serializeForXXXSort() API.
 * This implementation is used in a transition period until serializeForXXXSort() is removed.
 */
template <bool ascending>
class DefaultSortBlobWriter : public ISortBlobWriter {
private:
    const IAttributeVector& _attr;
    const common::BlobConverter* _converter;

public:
    DefaultSortBlobWriter(const IAttributeVector& attr, const common::BlobConverter* converter);
    long write(uint32_t docid, void* buf, long available) const override;
};

}
