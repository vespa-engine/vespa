// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "attributeguard.h"

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

void AttributeEnumGuard::takeLock() {
    if (valid()) {
        std::shared_lock<std::shared_timed_mutex> take(get().getEnumLock(),
                                                       std::defer_lock);
        _lock = std::move(take);
        _lock.lock();
    }
}

}
