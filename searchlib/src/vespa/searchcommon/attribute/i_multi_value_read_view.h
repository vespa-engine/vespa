// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multivalue.h"
#include <vespa/vespalib/datastore/atomic_entry_ref.h>
#include <vespa/vespalib/util/arrayref.h>

namespace search::attribute {

/**
 * Read view for the data stored in a multi-value attribute.
 * @tparam MultiValueType The multi-value type of the data to access.
 */
template <typename MultiValueType>
class IMultiValueReadView {
public:
    virtual ~IMultiValueReadView() = default;
    virtual vespalib::ConstArrayRef<MultiValueType> get_values(uint32_t docid) const = 0;
};

/**
 * Read view for the raw data stored in an array attribute.
 * @tparam T The value type of the raw data to access.
 */
template <typename T>
using IArrayReadView = IMultiValueReadView<T>;

/**
 * Read view for the raw data stored in a weighted set attribute.
 * @tparam T The value type of the raw data to access.
 */
template <typename T>
using IWeightedSetReadView = IMultiValueReadView<multivalue::WeightedValue<T>>;

/**
 * Read view for the raw data stored in an enumerated array attribute.
 */
using IArrayEnumReadView = IArrayReadView<vespalib::datastore::AtomicEntryRef>;

/**
 * Read view for the raw data stored in an enumerated weighted set attribute.
 */
using IWeightedSetEnumReadView = IWeightedSetReadView<vespalib::datastore::AtomicEntryRef>;

}
