// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query_term_filter_factory.h"
#include <vespa/searchsummary/docsummary/query_term_filter.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/hash_set.hpp>
#include <cassert>
#include <vespa/log/log.h>
LOG_SETUP(".vsm.query_term_filter_factory");

using search::docsummary::IQueryTermFilter;
using search::docsummary::IQueryTermFilterFactory;
using search::docsummary::QueryTermFilter;
using vespa::config::search::vsm::VsmfieldsConfig;
using vespa::config::search::vsm::VsmsummaryConfig;

namespace vsm {

QueryTermFilterFactory::QueryTermFilterFactory(VsmfieldsConfig& vsm_fields_config,
                                                 VsmsummaryConfig& vsm_summary_config)
    : IQueryTermFilterFactory(),
      _view_map(),
      _field_map()
{
    populate_view_map(vsm_fields_config);
    populate_field_map(vsm_summary_config);
}

QueryTermFilterFactory::~QueryTermFilterFactory() = default;

void
QueryTermFilterFactory::populate_view_map(VsmfieldsConfig& vsm_fields_config)
{
    for (auto& doctype : vsm_fields_config.documenttype) {
        for (auto& index : doctype.index) {
            for (auto& field : index.field) {
                _view_map[field.name].insert(index.name);
            }
        }
    }
}

void
QueryTermFilterFactory::populate_field_map(VsmsummaryConfig& vsm_summary_config)
{
    for (auto& summary_field : vsm_summary_config.fieldmap) {
        for (auto& document : summary_field.document) {
            _field_map[summary_field.summary].insert(document.field);
        }
    }
}

void
QueryTermFilterFactory::populate_views(StringSet& views, const vespalib::string& field) const
{
    auto itr = _view_map.find(field);
    if (itr != _view_map.end()) {
        for (auto& index : itr->second) {
            views.insert(index);
        }
    }
}

std::shared_ptr<const IQueryTermFilter>
QueryTermFilterFactory::make(vespalib::stringref input_field) const
{
    StringSet views;
    auto itr = _field_map.find(input_field);
    if (itr != _field_map.end()) {
        for (auto& field : itr->second) {
            populate_views(views, field);
        }
    } else {
        // Assume identity mapping vsm summary field -> document field
        populate_views(views, input_field);
    }
    return std::make_shared<QueryTermFilter>(std::move(views));
}

}
