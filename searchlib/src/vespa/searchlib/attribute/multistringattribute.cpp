// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multistringattribute.h"
#include "multistringattribute.hpp"
#include <vespa/searchlib/query/queryterm.h>

namespace search {

template class MultiValueStringAttributeT<EnumAttribute<StringAttribute>, multivalue::Value<EnumStoreBase::Index> >;
template class MultiValueStringAttributeT<EnumAttribute<StringAttribute>, multivalue::WeightedValue<EnumStoreBase::Index> >;

} // namespace search

