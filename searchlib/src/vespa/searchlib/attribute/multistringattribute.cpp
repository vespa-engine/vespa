// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multistringattribute.h"

namespace search {

template class MultiValueStringAttributeT<EnumAttribute<StringAttribute>, multivalue::Value<EnumStoreBase::Index> >;
template class MultiValueStringAttributeT<EnumAttribute<StringAttribute>, multivalue::WeightedValue<EnumStoreBase::Index> >;

} // namespace search

