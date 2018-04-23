// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "componentguard.h"
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <shared_mutex>

namespace search {

class AttributeVector;

/**
 * This class makes sure that you will have a consistent view per document in the attribute vector
 * while the guard is held.
 **/
class AttributeGuard : public ComponentGuard<AttributeVector>
{
public:
    using UP = std::unique_ptr<AttributeGuard>;
    using SP = std::shared_ptr<AttributeGuard>;
    using AttributeVectorSP = std::shared_ptr<AttributeVector>;
    AttributeGuard();
    AttributeGuard(const AttributeVectorSP & attribute);
};

}

