// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_store_dynamic_type_mapper.hpp"

namespace vespalib::datastore {

template class ArrayStoreDynamicTypeMapper<char>;
template class ArrayStoreDynamicTypeMapper<int8_t>;
template class ArrayStoreDynamicTypeMapper<int16_t>;
template class ArrayStoreDynamicTypeMapper<int32_t>;
template class ArrayStoreDynamicTypeMapper<int64_t>;
template class ArrayStoreDynamicTypeMapper<float>;
template class ArrayStoreDynamicTypeMapper<double>;
template class ArrayStoreDynamicTypeMapper<AtomicEntryRef>;

}
