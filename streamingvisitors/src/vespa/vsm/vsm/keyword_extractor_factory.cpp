// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "keyword_extractor_factory.h"
#include <vespa/searchsummary/docsummary/keyword_extractor.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/hash_set.hpp>
#include <cassert>
#include <vespa/log/log.h>
LOG_SETUP(".vsm.keyword_extractor_factory");

using search::docsummary::IKeywordExtractor;
using search::docsummary::IKeywordExtractorFactory;
using search::docsummary::KeywordExtractor;
using vespa::config::search::vsm::VsmfieldsConfig;
using vespa::config::search::vsm::VsmsummaryConfig;

namespace vsm {

KeywordExtractorFactory::KeywordExtractorFactory(VsmfieldsConfig& vsm_fields_config,
                                                 VsmsummaryConfig& vsm_summary_config)
    : IKeywordExtractorFactory(),
      _index_map(),
      _field_map()
{
    populate_index_map(vsm_fields_config);
    populate_field_map(vsm_summary_config);
}

KeywordExtractorFactory::~KeywordExtractorFactory() = default;

void
KeywordExtractorFactory::populate_index_map(VsmfieldsConfig& vsm_fields_config)
{
    for (auto& doctype : vsm_fields_config.documenttype) {
        for (auto& index : doctype.index) {
            for (auto& field : index.field) {
                _index_map[field.name].insert(index.name);
            }
        }
    }
}

void
KeywordExtractorFactory::populate_field_map(VsmsummaryConfig& vsm_summary_config)
{
    for (auto& summary_field : vsm_summary_config.fieldmap) {
        for (auto& document : summary_field.document) {
            _field_map[summary_field.summary].insert(document.field);
        }
    }
}

void
KeywordExtractorFactory::populate_indexes(StringSet& indexes, const vespalib::string& field) const
{
    auto itr = _index_map.find(field);
    if (itr != _index_map.end()) {
        for (auto& index : itr->second) {
            indexes.insert(index);
        }
    }
}

std::shared_ptr<const IKeywordExtractor>
KeywordExtractorFactory::make(vespalib::stringref input_field) const
{
    StringSet indexes;
    auto itr = _field_map.find(input_field);
    if (itr != _field_map.end()) {
        for (auto& field : itr->second) {
            populate_indexes(indexes, field);
        }
    } else {
        // Assume identity mapping vsm summary field -> document field
        populate_indexes(indexes, input_field);
    }
    return std::make_shared<KeywordExtractor>(std::move(indexes));
}

}
