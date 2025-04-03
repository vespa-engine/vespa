// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "single_numeric_sort_blob_writer.h"
#include "floatbase.hpp"
#include "integerbase.hpp"

namespace search::attribute {

template <typename AttrType, bool ascending>
SingleNumericSortBlobWriter<AttrType, ascending>::SingleNumericSortBlobWriter(const AttrType& attr)
    : _attr(attr)
{
}

template <typename AttrType, bool ascending>
long
SingleNumericSortBlobWriter<AttrType, ascending>::write(uint32_t docid, void* buf, long available)
{
    T orig_value = _attr.get(docid);
    return vespalib::serializeForSort<vespalib::convertForSort<T, ascending>>(orig_value, buf, available);
}

template class SingleNumericSortBlobWriter<IntegerAttributeTemplate<int8_t>, true>;
template class SingleNumericSortBlobWriter<IntegerAttributeTemplate<int16_t>, true>;
template class SingleNumericSortBlobWriter<IntegerAttributeTemplate<int32_t>, true>;
template class SingleNumericSortBlobWriter<IntegerAttributeTemplate<int64_t>, true>;
template class SingleNumericSortBlobWriter<FloatingPointAttributeTemplate<float>, true>;
template class SingleNumericSortBlobWriter<FloatingPointAttributeTemplate<double>, true>;

template class SingleNumericSortBlobWriter<IntegerAttributeTemplate<int8_t>, false>;
template class SingleNumericSortBlobWriter<IntegerAttributeTemplate<int16_t>, false>;
template class SingleNumericSortBlobWriter<IntegerAttributeTemplate<int32_t>, false>;
template class SingleNumericSortBlobWriter<IntegerAttributeTemplate<int64_t>, false>;
template class SingleNumericSortBlobWriter<FloatingPointAttributeTemplate<float>, false>;
template class SingleNumericSortBlobWriter<FloatingPointAttributeTemplate<double>, false>;

}
