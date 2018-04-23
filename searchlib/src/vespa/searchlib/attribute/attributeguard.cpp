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

template class ComponentGuard<AttributeVector>;

}
