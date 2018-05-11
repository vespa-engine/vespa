// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "componentguard.h"

namespace search {

template <typename T>
ComponentGuard<T>::ComponentGuard()  = default;

template <typename T>
ComponentGuard<T>::ComponentGuard(const Component & component) :
    _component(component),
    _generationGuard(valid() ? _component->takeGenerationGuard() : Guard())
{ }

template <typename T>
ComponentGuard<T>::ComponentGuard(const ComponentGuard &) = default;

template <typename T>
ComponentGuard<T>::ComponentGuard(ComponentGuard &&) = default;

template <typename T>
ComponentGuard<T> & ComponentGuard<T>::operator = (ComponentGuard &&) = default;

template <typename T>
ComponentGuard<T> &
ComponentGuard<T>::operator = (const ComponentGuard & rhs) {
    ComponentGuard<T> tmp(rhs);
    *this = std::move(tmp);
    return *this;
}

template <typename T>
ComponentGuard<T>::~ComponentGuard() = default;

}
