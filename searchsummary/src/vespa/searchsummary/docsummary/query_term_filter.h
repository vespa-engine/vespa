// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_query_term_filter.h"
#include <vespa/vespalib/stllike/hash_set.h>

namespace search::docsummary {

/*
 * Class for checking if query term view indicates that
 * related query term is useful from the perspective of juniper.
 */
class QueryTermFilter : public IQueryTermFilter
{
    using StringSet = vespalib::hash_set<vespalib::string>;
    StringSet _views;
public:
    QueryTermFilter(StringSet views);
    ~QueryTermFilter() override;
    bool use_view(vespalib::stringref view) const override;
};

}
