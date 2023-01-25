// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query_term_filter.h"
#include <vespa/vespalib/stllike/hash_set.hpp>

namespace search::docsummary {

QueryTermFilter::QueryTermFilter(StringSet views)
    : IQueryTermFilter(),
      _views(std::move(views))
{
    if (_views.contains("default")) {
        _views.insert("");
    }
}

QueryTermFilter::~QueryTermFilter() = default;

bool
QueryTermFilter::use_view(vespalib::stringref view) const
{
    return _views.contains(view);
}

}
