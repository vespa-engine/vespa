// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "singlestringattribute.h"
#include "singlestringattribute.hpp"
#include <vespa/log/log.h>

LOG_SETUP(".searchlib.attribute.singlestringattribute");
namespace search {

template class SingleValueStringAttributeT<EnumAttribute<StringAttribute>>; 

} // namespace search

