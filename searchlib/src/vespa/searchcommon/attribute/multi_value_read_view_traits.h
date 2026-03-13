// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_multi_value_read_view.h"

namespace vespalib::datastore { class AtomicEntryRef; }

namespace search::attribute {

class IArrayBoolReadView;

template <typename T> struct ArrayReadViewType;
template <> struct ArrayReadViewType<bool> { using type = IArrayBoolReadView; };
template <> struct ArrayReadViewType<int8_t> { using type = IArrayReadView<int8_t>; };
template <> struct ArrayReadViewType<int16_t> { using type = IArrayReadView<int16_t>; };
template <> struct ArrayReadViewType<int32_t> { using type = IArrayReadView<int32_t>; };
template <> struct ArrayReadViewType<int64_t> { using type = IArrayReadView<int64_t>; };
template <> struct ArrayReadViewType<float> { using type = IArrayReadView<float>; };
template <> struct ArrayReadViewType<double> { using type = IArrayReadView<double>; };
template <> struct ArrayReadViewType<const char*> { using type = IArrayReadView<const char*>; };
template <> struct ArrayReadViewType<vespalib::datastore::AtomicEntryRef> { using type = IArrayReadView<vespalib::datastore::AtomicEntryRef>; };

/*
 * Utility to get read view type for an array attribute containing values of type T
 */
template <typename T> using ArrayReadViewType_t = typename ArrayReadViewType<T>::type;

}
