// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "single_numeric_sort_blob_writer.h"
#include "string_to_number.h"
#include "floatbase.hpp"
#include "integerbase.hpp"

using search::common::sortspec::MissingPolicy;

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

template <typename AttrType, bool ascending>
SingleNumericMissingSortBlobWriter<AttrType, ascending>::SingleNumericMissingSortBlobWriter(const AttrType &attr,
                                                                                            MissingPolicy policy,
                                                                                            T missing_value)
    : _attr(attr),
      _writer(policy, missing_value, false)
{
}

template <typename AttrType, bool ascending>
long
SingleNumericMissingSortBlobWriter<AttrType, ascending>::write(uint32_t docid, void* buf, long available)
{
    _writer.reset();
    T value = _attr.get(docid);
    if (!attribute::isUndefined(value)) {
        _writer.candidate(value);
    }
    return _writer.write(buf, available);
}

template <typename AttrType>
std::unique_ptr<attribute::ISortBlobWriter>
make_single_numeric_sort_blob_writer(const AttrType& attr, bool ascending,
                                     MissingPolicy policy,
                                     std::string_view missing_value)
{
    using T = typename AttrType::BaseType;
    if (ascending) {
        if (policy == MissingPolicy::DEFAULT) {
            return std::make_unique<SingleNumericSortBlobWriter<AttrType, true>>(attr);
        } else {
            return std::make_unique<SingleNumericMissingSortBlobWriter<AttrType, true>>(attr, policy,
                                                                                        string_to_number<T>(missing_value));
        }
    } else {
        if (policy == MissingPolicy::DEFAULT) {
            return std::make_unique<SingleNumericSortBlobWriter<AttrType, false>>(attr);
        } else {
            return std::make_unique<SingleNumericMissingSortBlobWriter<AttrType, false>>(attr, policy,
                                                                                         string_to_number<T>(missing_value));
        }
    }
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

template class SingleNumericMissingSortBlobWriter<IntegerAttributeTemplate<int8_t>, true>;
template class SingleNumericMissingSortBlobWriter<IntegerAttributeTemplate<int16_t>, true>;
template class SingleNumericMissingSortBlobWriter<IntegerAttributeTemplate<int32_t>, true>;
template class SingleNumericMissingSortBlobWriter<IntegerAttributeTemplate<int64_t>, true>;
template class SingleNumericMissingSortBlobWriter<FloatingPointAttributeTemplate<float>, true>;
template class SingleNumericMissingSortBlobWriter<FloatingPointAttributeTemplate<double>, true>;

template class SingleNumericMissingSortBlobWriter<IntegerAttributeTemplate<int8_t>, false>;
template class SingleNumericMissingSortBlobWriter<IntegerAttributeTemplate<int16_t>, false>;
template class SingleNumericMissingSortBlobWriter<IntegerAttributeTemplate<int32_t>, false>;
template class SingleNumericMissingSortBlobWriter<IntegerAttributeTemplate<int64_t>, false>;
template class SingleNumericMissingSortBlobWriter<FloatingPointAttributeTemplate<float>, false>;
template class SingleNumericMissingSortBlobWriter<FloatingPointAttributeTemplate<double>, false>;

template std::unique_ptr<attribute::ISortBlobWriter>
make_single_numeric_sort_blob_writer<IntegerAttributeTemplate<int8_t>>(const IntegerAttributeTemplate<int8_t>&, bool, MissingPolicy, std::string_view);
template std::unique_ptr<attribute::ISortBlobWriter>
make_single_numeric_sort_blob_writer<IntegerAttributeTemplate<int16_t>>(const IntegerAttributeTemplate<int16_t>&, bool, MissingPolicy, std::string_view);
template std::unique_ptr<attribute::ISortBlobWriter>
make_single_numeric_sort_blob_writer<IntegerAttributeTemplate<int32_t>>(const IntegerAttributeTemplate<int32_t>&, bool, MissingPolicy, std::string_view);
template std::unique_ptr<attribute::ISortBlobWriter>
make_single_numeric_sort_blob_writer<IntegerAttributeTemplate<int64_t>>(const IntegerAttributeTemplate<int64_t>&, bool, MissingPolicy, std::string_view);
template std::unique_ptr<attribute::ISortBlobWriter>
make_single_numeric_sort_blob_writer<FloatingPointAttributeTemplate<float>>(const FloatingPointAttributeTemplate<float>&, bool, MissingPolicy, std::string_view);
template std::unique_ptr<attribute::ISortBlobWriter>
make_single_numeric_sort_blob_writer<FloatingPointAttributeTemplate<double>>(const FloatingPointAttributeTemplate<double>&, bool, MissingPolicy, std::string_view);

}
