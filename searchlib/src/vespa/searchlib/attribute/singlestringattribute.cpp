// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "singlestringattribute.h"
#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.single_string_attribute");
#include "singlestringattribute.hpp"

namespace search {

template class SingleValueStringAttributeT<EnumAttribute<StringAttribute>>; 

} // namespace search

