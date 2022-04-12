// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multi_string_enum_search_context.hpp"
#include <vespa/searchcommon/attribute/multivalue.h>

using ValueRef = vespalib::datastore::AtomicEntryRef;
using WeightedValueRef = search::multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>;

namespace search::attribute {

template class MultiStringEnumSearchContext<ValueRef>;

template class MultiStringEnumSearchContext<WeightedValueRef>;

}
