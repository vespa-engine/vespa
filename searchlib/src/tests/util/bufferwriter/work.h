// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>

namespace search {

class BufferWriter;

template <class T>
using WorkFunc = void (*)(const std::vector<T> &v, BufferWriter &writer);
template <class T>
void work(const std::vector<T> &v, BufferWriter &writer);
template <class T>
void workLambda(const std::vector<T> &v, BufferWriter &writer);
template <class T>
void workFunctor(const std::vector<T> &v, BufferWriter &writer);
template <class T>
void workFunctor2(const std::vector<T> &v, BufferWriter &writer);

} // namespace search
