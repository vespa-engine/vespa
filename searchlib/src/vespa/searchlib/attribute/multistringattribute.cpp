// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multistringattribute.h"
#include "multistringattribute.hpp"

namespace search {

template class MultiValueStringAttributeT<EnumAttribute<StringAttribute>, multivalue::Value<IEnumStore::Index> >;
template class MultiValueStringAttributeT<EnumAttribute<StringAttribute>, multivalue::WeightedValue<IEnumStore::Index> >;

} // namespace search

