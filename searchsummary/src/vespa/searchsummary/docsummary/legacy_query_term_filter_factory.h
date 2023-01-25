// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_query_term_filter_factory.h"

namespace search::docsummary {

/*
 * Class for creating an instance of IQueryTermFilter.
 */
class LegacyQueryTermFilterFactory : public IQueryTermFilterFactory
{
    std::shared_ptr<const IQueryTermFilter> _query_term_filter;
public:
    explicit LegacyQueryTermFilterFactory(std::shared_ptr<const IQueryTermFilter> query_term_filter);
    virtual ~LegacyQueryTermFilterFactory();
    std::shared_ptr<const IQueryTermFilter> make(vespalib::stringref) const override;
};

}
