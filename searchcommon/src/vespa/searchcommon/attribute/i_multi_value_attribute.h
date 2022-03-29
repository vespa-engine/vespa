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
    virtual ~IMultiValueAttribute() {}
    virtual const IArrayReadView<int8_t>* as_int8_array() const = 0;
    virtual const IArrayReadView<int32_t>* as_int32_array() const = 0;
    virtual const IArrayReadView<int64_t>* as_int64_array() const = 0;
    virtual const IArrayReadView<float>* as_float_array() const = 0;
    virtual const IArrayReadView<double>* as_double_array() const = 0;

    virtual const IWeightedSetReadView<int8_t>* as_int8_wset() const = 0;
    virtual const IWeightedSetReadView<int32_t>* as_int32_wset() const = 0;
    virtual const IWeightedSetReadView<int64_t>* as_int64_wset() const = 0;
    virtual const IWeightedSetReadView<float>* as_float_wset() const = 0;
    virtual const IWeightedSetReadView<double>* as_double_wset() const = 0;

    virtual const IWeightedSetEnumReadView* as_enum_wset() const = 0;
};

}
