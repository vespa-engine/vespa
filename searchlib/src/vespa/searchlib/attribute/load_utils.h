// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributevector.h"

namespace search {
namespace attribute {

/*
 * Function for loading mapping from document id to array of enum indexes
 * or values from enumerated attribute reader.
 */
template <class MvMapping, class Saver>
uint32_t
loadFromEnumeratedMultiValue(MvMapping &mapping,
                             AttributeVector::ReaderBase &attrReader,
                             vespalib::ConstArrayRef<typename MvMapping::MultiValueType::ValueType> map,
                             Saver saver) __attribute((noinline));

} // namespace search::attribute
} // namespace search
