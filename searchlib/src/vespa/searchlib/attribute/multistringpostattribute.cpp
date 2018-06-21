// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multistringpostattribute.h"
#include "multistringpostattribute.hpp"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.multi_string_post_attribute");

namespace search {

EnumStoreBase::Index
StringEnumIndexMapper::map(EnumStoreBase::Index original, const EnumStoreComparator & compare) const
{
    return _dictionary.find(original, compare).getKey();
}

} // namespace search

