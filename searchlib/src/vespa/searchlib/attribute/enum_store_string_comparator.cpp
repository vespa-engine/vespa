// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enum_store_string_comparator.h"

#include <vespa/searchlib/util/foldedstringcompare.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.enum_store_string_comparator");

namespace search {

bool EnumStoreStringComparator::less(vespalib::datastore::EntryRef lhs,
                                     vespalib::datastore::EntryRef rhs) const noexcept {
    auto l_val = get(lhs);
    auto r_val = get(rhs);
    auto p_len = _prefix_len;

    return std::visit([=](const auto& strategy) noexcept { return strategy.less(l_val, r_val, p_len); }, _strategy);
}

} // namespace search
