// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multi_enum_search_context.hpp"
#include "string_search_context.h"

using ValueRef = vespalib::datastore::AtomicEntryRef;
using WeightedValueRef = search::multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>;

namespace search::attribute {

template class MultiEnumSearchContext<const char *, StringSearchContext, ValueRef>;

template class MultiEnumSearchContext<const char *, StringSearchContext, WeightedValueRef>;

}
