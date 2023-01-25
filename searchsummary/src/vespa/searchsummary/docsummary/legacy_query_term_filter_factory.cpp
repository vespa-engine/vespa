// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "legacy_query_term_filter_factory.h"

namespace search::docsummary {

LegacyQueryTermFilterFactory::LegacyQueryTermFilterFactory(std::shared_ptr<const IQueryTermFilter> query_term_filter)
    : IQueryTermFilterFactory(),
      _query_term_filter(std::move(query_term_filter))
{
}

LegacyQueryTermFilterFactory::~LegacyQueryTermFilterFactory() = default;

std::shared_ptr<const IQueryTermFilter>
LegacyQueryTermFilterFactory::make(vespalib::stringref) const
{
    return _query_term_filter;
}

}
