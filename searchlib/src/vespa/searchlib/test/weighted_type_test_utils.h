// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcommon/attribute/iattributevector.h>
#include <type_traits>

template <typename T> struct IsWeightedType : std::false_type {};
template <typename T> struct IsWeightedType<search::attribute::WeightedType<T>> : std::true_type {};

struct value_then_weight_order {
    template <typename T>
    bool operator()(const T& lhs, const T& rhs) const noexcept {
        if (lhs.getValue() != rhs.getValue()) {
            return (lhs.getValue() < rhs.getValue());
        }
        return (lhs.getWeight() < rhs.getWeight());
    }
};

struct order_by_value {
    template <typename T>
    bool operator()(const T& lhs, const T& rhs) const noexcept {
        if constexpr (IsWeightedType<T>::value) {
            return (lhs.getValue() < rhs.getValue());
        } else {
            return (lhs < rhs);
        }
    }
};


struct order_by_weight {
    template <typename T>
    bool operator()(const search::attribute::WeightedType<T>& lhs,
                    const search::attribute::WeightedType<T>& rhs) const noexcept {
        return (lhs.getWeight() < rhs.getWeight());
    }
};
