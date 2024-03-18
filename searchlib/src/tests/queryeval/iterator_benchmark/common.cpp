// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "common.h"
#include <sstream>

using search::attribute::CollectionType;

namespace search::queryeval::test {

vespalib::string
to_string(const Config& attr_config)
{
    std::ostringstream oss;
    auto col_type = attr_config.collectionType();
    auto basic_type = attr_config.basicType();
    if (col_type == CollectionType::SINGLE) {
        oss << basic_type.asString();
    } else {
        oss << col_type.asString() << "<" << basic_type.asString() << ">";
    }
    if (attr_config.fastSearch()) {
        oss << "(fs)";
    }
    return oss.str();
}

vespalib::string
to_string(QueryOperator query_op)
{
    switch (query_op) {
        case QueryOperator::Term: return "Term";
        case QueryOperator::In: return "In";
        case QueryOperator::WeightedSet: return "WeightedSet";
        case QueryOperator::DotProduct: return "DotProduct";
        case QueryOperator::And: return "And";
        case QueryOperator::Or: return "Or";
    }
    return "unknown";
}

}