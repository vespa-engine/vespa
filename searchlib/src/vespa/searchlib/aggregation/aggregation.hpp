// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/resultnode.h>

#include <memory>

namespace search::aggregation {

#define IMPLEMENT_ABSTRACT_AGGREGATIONRESULT(cclass, base)                 \
    IMPLEMENT_IDENTIFIABLE_ABSTRACT_NS2(search, aggregation, cclass, base)

#define IMPLEMENT_AGGREGATIONRESULT(cclass, base) IMPLEMENT_IDENTIFIABLE_NS2(search, aggregation, cclass, base)

inline bool is_ready(const expression::ResultNode* my_result, const expression::ResultNode& ref) {
    return (my_result != nullptr && my_result->getClass().id() == ref.getClass().id());
}

template <typename Wanted, typename Fallback>
std::unique_ptr<Wanted> create_and_ensure_wanted(const expression::ResultNode& result) {
    std::unique_ptr<expression::ResultNode> tmp = result.createBaseType();
    if (dynamic_cast<Wanted*>(tmp.get()) != nullptr) {
        return std::unique_ptr<Wanted>(static_cast<Wanted*>(tmp.release()));
    } else {
        return std::make_unique<Fallback>();
    }
}

} // namespace search::aggregation
