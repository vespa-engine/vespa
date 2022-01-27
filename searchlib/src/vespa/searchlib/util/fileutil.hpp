// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fileutil.h"

namespace search {

template <typename T>
SequentialReadModifyWriteVector<T>::SequentialReadModifyWriteVector()
    : Vector(),
      _rp(0),
      _wp(0)
{ }

template <typename T>
SequentialReadModifyWriteVector<T>::SequentialReadModifyWriteVector(size_t sz)
    : Vector(sz),
      _rp(0),
      _wp(0)
{ }

template <typename T>
SequentialReadModifyWriteVector<T>::~SequentialReadModifyWriteVector() = default;

}
