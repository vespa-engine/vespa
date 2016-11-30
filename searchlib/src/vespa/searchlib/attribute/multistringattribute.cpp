// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "multistringattribute.h"
#include "multistringattribute.hpp"
#include <vespa/log/log.h>

LOG_SETUP(".searchlib.attribute.multistringattribute");
namespace search {

template class MultiValueStringAttributeT<EnumAttribute<StringAttribute>, multivalue::Value<EnumStoreBase::Index> >;
template class MultiValueStringAttributeT<EnumAttribute<StringAttribute>, multivalue::WeightedValue<EnumStoreBase::Index> >;

} // namespace search

