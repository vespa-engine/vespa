// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_multi_value_read_view.h"

namespace vespalib { class Stash; }

namespace search::attribute {

/**
 * Interface that provides read views for different multi-value attribute types.
 *
 * The type-safe down-cast functions only return a valid pointer when that particular type is supported.
 * Otherwise a nullptr is returned.
 * The returned read view is owned by the supplied stash.
 */
class IMultiValueAttribute {
public:
    template<typename MultiValueType>
    class MultiValueTag {};

    template<typename T>
    using ArrayTag = MultiValueTag<T>;

    using ArrayEnumTag = ArrayTag<vespalib::datastore::AtomicEntryRef>;

    template<typename T>
    using WeightedSetTag = MultiValueTag<search::multivalue::WeightedValue<T>>;

    using WeightedSetEnumTag = WeightedSetTag<vespalib::datastore::AtomicEntryRef>;

    virtual ~IMultiValueAttribute() = default;

    virtual const IArrayReadView<int8_t>* make_read_view(ArrayTag<int8_t>, vespalib::Stash&) const { return nullptr; }
    virtual const IArrayReadView<int16_t>* make_read_view(ArrayTag<int16_t>, vespalib::Stash&) const { return nullptr; }
    virtual const IArrayReadView<int32_t>* make_read_view(ArrayTag<int32_t>, vespalib::Stash&) const { return nullptr; }
    virtual const IArrayReadView<int64_t>* make_read_view(ArrayTag<int64_t>, vespalib::Stash&) const { return nullptr; }
    virtual const IArrayReadView<float>* make_read_view(ArrayTag<float>, vespalib::Stash&) const { return nullptr; }
    virtual const IArrayReadView<double>* make_read_view(ArrayTag<double>, vespalib::Stash&) const { return nullptr; }
    virtual const IArrayReadView<const char*>* make_read_view(ArrayTag<const char*>, vespalib::Stash&) const { return nullptr; }

    virtual const IWeightedSetReadView<int8_t>* make_read_view(WeightedSetTag<int8_t>, vespalib::Stash&) const { return nullptr; }
    virtual const IWeightedSetReadView<int16_t>* make_read_view(WeightedSetTag<int16_t>, vespalib::Stash&) const { return nullptr; }
    virtual const IWeightedSetReadView<int32_t>* make_read_view(WeightedSetTag<int32_t>, vespalib::Stash&) const { return nullptr; }
    virtual const IWeightedSetReadView<int64_t>* make_read_view(WeightedSetTag<int64_t>, vespalib::Stash&) const { return nullptr; }
    virtual const IWeightedSetReadView<float>* make_read_view(WeightedSetTag<float>, vespalib::Stash&) const { return nullptr; }
    virtual const IWeightedSetReadView<double>* make_read_view(WeightedSetTag<double>, vespalib::Stash&) const { return nullptr; }
    virtual const IWeightedSetReadView<const char*>* make_read_view(WeightedSetTag<const char*>, vespalib::Stash&) const { return nullptr; }

    virtual const IArrayEnumReadView* make_read_view(ArrayEnumTag, vespalib::Stash&) const { return nullptr; }
    virtual const IWeightedSetEnumReadView* make_read_view(WeightedSetEnumTag, vespalib::Stash&) const { return nullptr; }
};

}
