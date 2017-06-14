// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "diversity.h"

namespace search {
namespace attribute {
namespace diversity {

template <typename ITR>
ForwardRange<ITR>::ForwardRange(const ForwardRange &) = default;

template <typename ITR>
ForwardRange<ITR>::ForwardRange(const ITR &lower, const ITR &upper)
    : _lower(lower),
      _upper(upper)
{}

template <typename ITR>
ForwardRange<ITR>::~ForwardRange() { }

template <typename ITR>
ReverseRange<ITR>::ReverseRange(const ReverseRange &) = default;

template <typename ITR>
ReverseRange<ITR>::ReverseRange(const ITR &lower, const ITR &upper)
    : _lower(lower),
      _upper(upper)
{}


template <typename ITR>
ReverseRange<ITR>::~ReverseRange() { }

}
}
}
