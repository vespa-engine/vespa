// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query_term_filter_factory.h"
#include "query_term_filter.h"
#include <vespa/searchcommon/common/schema.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/hash_set.hpp>

namespace search::docsummary {

QueryTermFilterFactory::QueryTermFilterFactory(const search::index::Schema& schema)
    : IQueryTermFilterFactory(),
      _view_map()
{
    for (uint32_t i = 0; i < schema.getNumFieldSets(); ++i) {
        auto& field_set = schema.getFieldSet(i);
        auto& fields = field_set.getFields();
        for (auto& field : fields) {
            auto& vec = _view_map[field];
            vec.emplace_back(field_set.getName());
        }
    }
}

QueryTermFilterFactory::~QueryTermFilterFactory() = default;

std::shared_ptr<const IQueryTermFilter>
QueryTermFilterFactory::make(vespalib::stringref input_field) const
{
    vespalib::hash_set<vespalib::string> views;
    views.insert(input_field);
    auto itr = _view_map.find(input_field);
    if (itr != _view_map.end()) {
        for (auto& index : itr->second) {
            views.insert(index);
        }
    }
    return std::make_shared<QueryTermFilter>(std::move(views));
}

}
