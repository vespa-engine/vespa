// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "numeric_sort_blob_writer.h"
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
    long write(uint32_t docid, void* buf, long available) override;
};

/**
 * Class used to write sort blobs for single numeric attributes that handles missing values
 * using a given missing policy.
 */
template <typename AttrType, bool ascending>
class SingleNumericMissingSortBlobWriter : public ISortBlobWriter {
private:
    using T = typename AttrType::BaseType;
    const AttrType& _attr;
    NumericSortBlobWriter<T, ascending> _writer;
public:
    SingleNumericMissingSortBlobWriter(const AttrType& attr,
                                       search::common::sortspec::MissingPolicy policy,
                                       T missing_value);
    long write(uint32_t docid, void* buf, long available) override;
};

template <typename AttrType>
std::unique_ptr<attribute::ISortBlobWriter>
make_single_numeric_sort_blob_writer(const AttrType& attr, bool ascending,
                                     common::sortspec::MissingPolicy policy,
                                     std::string_view missing_value);

}
