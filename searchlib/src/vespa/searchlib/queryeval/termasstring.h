// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/query/tree/location.h>
#include <vespa/searchlib/query/tree/range.h>
#include <string>

namespace search {
namespace query { class Node; }

namespace queryeval {

inline const vespalib::string &termAsString(const vespalib::string &term) {
    return term;
}

vespalib::string termAsString(double float_term);

vespalib::string termAsString(int64_t int_term);

vespalib::string termAsString(const search::query::Range &term);

vespalib::string termAsString(const search::query::Location &term);

vespalib::string termAsString(const search::query::Node &term_node);

} // namespace search::queryeval
} // namespace search

