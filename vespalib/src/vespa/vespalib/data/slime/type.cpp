// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "type.h"

namespace vespalib::slime {

template<int T> const uint32_t TypeType<T>::ID;
template<int T> const TypeType<T> TypeType<T>::instance;

template struct TypeType<NIX::ID>;
template struct TypeType<BOOL::ID>;
template struct TypeType<LONG::ID>;
template struct TypeType<DOUBLE::ID>;
template struct TypeType<STRING::ID>;
template struct TypeType<DATA::ID>;
template struct TypeType<ARRAY::ID>;
template struct TypeType<OBJECT::ID>;

} // namespace vespalib::slime
