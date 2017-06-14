// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributeguard.h"
#include "componentguard.hpp"
#include "attributevector.h"

namespace search {

AttributeGuard::AttributeGuard() :
    ComponentGuard<AttributeVector>()
{
}

AttributeGuard::AttributeGuard(const AttributeVector::SP & attr) :
    ComponentGuard<AttributeVector>(attr)
{
}

AttributeEnumGuard::AttributeEnumGuard(const AttributeVector::SP & attr) :
    AttributeGuard(attr),
    _lock()
{
    takeLock();
}

AttributeEnumGuard::AttributeEnumGuard(const AttributeGuard & attr) :
    AttributeGuard(attr),
    _lock()
{
    takeLock();
}

AttributeEnumGuard::~AttributeEnumGuard() { }

void AttributeEnumGuard::takeLock() {
    if (valid()) {
        std::shared_lock<std::shared_timed_mutex> take(get()->getEnumLock(),
                                                       std::defer_lock);
        _lock = std::move(take);
        _lock.lock();
    }
}

template class ComponentGuard<AttributeVector>;

}
