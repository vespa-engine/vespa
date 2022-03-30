// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_multi_value_read_view.h"

namespace search::attribute {

/**
 * Interface that provides read views for different multi-value attribute types.
 *
 * The type-safe down-cast functions only return a valid pointer when that particular type is supported.
 * Otherwise a nullptr is returned.
 */
class IMultiValueAttribute {
public:
    template<typename MultiValueType>
    class Tag {};

    template<typename T>
    using ArrayTag = Tag<search::multivalue::Value<T>>;

    using ArrayEnumTag = ArrayTag<vespalib::datastore::AtomicEntryRef>;

    template<typename T>
    using WeightedSetTag = Tag<search::multivalue::WeightedValue<T>>;

    using WeightedSetEnumTag = WeightedSetTag<vespalib::datastore::AtomicEntryRef>;

    virtual ~IMultiValueAttribute() {}

    virtual const IArrayReadView<int8_t>* as_read_view(ArrayTag<int8_t>) const { return nullptr; }
    virtual const IArrayReadView<int16_t>* as_read_view(ArrayTag<int16_t>) const { return nullptr; }
    virtual const IArrayReadView<int32_t>* as_read_view(ArrayTag<int32_t>) const { return nullptr; }
    virtual const IArrayReadView<int64_t>* as_read_view(ArrayTag<int64_t>) const { return nullptr; }
    virtual const IArrayReadView<float>* as_read_view(ArrayTag<float>) const { return nullptr; }
    virtual const IArrayReadView<double>* as_read_view(ArrayTag<double>) const { return nullptr; }

    virtual const IWeightedSetReadView<int8_t>* as_read_view(WeightedSetTag<int8_t>) const { return nullptr; }
    virtual const IWeightedSetReadView<int16_t>* as_read_view(WeightedSetTag<int16_t>) const { return nullptr; }
    virtual const IWeightedSetReadView<int32_t>* as_read_view(WeightedSetTag<int32_t>) const { return nullptr; }
    virtual const IWeightedSetReadView<int64_t>* as_read_view(WeightedSetTag<int64_t>) const { return nullptr; }
    virtual const IWeightedSetReadView<float>* as_read_view(WeightedSetTag<float>) const { return nullptr; }
    virtual const IWeightedSetReadView<double>* as_read_view(WeightedSetTag<double>) const { return nullptr; }

    virtual const IArrayEnumReadView* as_read_view(ArrayEnumTag) const { return nullptr; }
    virtual const IWeightedSetEnumReadView* as_read_view(WeightedSetEnumTag) const { return nullptr; }
};

}
