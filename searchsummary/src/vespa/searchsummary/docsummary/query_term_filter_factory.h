// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_query_term_filter_factory.h"
#include <vespa/searchcommon/common/schema.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vector>

namespace search::index { class Schema; }

namespace search::docsummary {

/*
 * Class for creating an instance of IQueryTermFilter.
 */
class QueryTermFilterFactory : public IQueryTermFilterFactory
{
    vespalib::hash_map<vespalib::string, std::vector<vespalib::string>> _view_map;
public:
    QueryTermFilterFactory(const search::index::Schema& schema);
    ~QueryTermFilterFactory() override;
    std::shared_ptr<const IQueryTermFilter> make(vespalib::stringref input_field) const override;
};

}
