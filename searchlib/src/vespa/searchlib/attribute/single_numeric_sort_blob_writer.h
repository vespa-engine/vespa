// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcommon/attribute/i_sort_blob_writer.h>

namespace search::attribute {

/**
 * Class used to write sort blobs for single numeric attributes.
 */
template <typename AttrType, bool ascending>
class SingleNumericSortBlobWriter : public ISortBlobWriter {
private:
    using T = typename AttrType::BaseType;
    const AttrType& _attr;
public:
    SingleNumericSortBlobWriter(const AttrType& attr);
    long write(uint32_t docid, void* buf, long available) const override;
};

}
