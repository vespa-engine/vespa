// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "collection_type.h"

namespace search {
namespace fef {

CollectionType::CollectionType(uint32_t value)
    : _value(value)
{
}

const CollectionType CollectionType::SINGLE(1);

const CollectionType CollectionType::ARRAY(2);

const CollectionType CollectionType::WEIGHTEDSET(3);

} // namespace fef
} // namespace search
