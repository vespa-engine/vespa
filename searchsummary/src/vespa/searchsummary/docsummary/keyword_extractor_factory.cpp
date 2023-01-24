// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "keyword_extractor_factory.h"
#include "keyword_extractor.h"
#include <vespa/searchcommon/common/schema.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/hash_set.hpp>

namespace search::docsummary {

KeywordExtractorFactory::KeywordExtractorFactory(const search::index::Schema& schema)
    : IKeywordExtractorFactory(),
      _index_map()
{
    for (uint32_t i = 0; i < schema.getNumFieldSets(); ++i) {
        auto& field_set = schema.getFieldSet(i);
        auto& fields = field_set.getFields();
        for (auto& field : fields) {
            auto& vec = _index_map[field];
            vec.emplace_back(field_set.getName());
        }
    }
}

KeywordExtractorFactory::~KeywordExtractorFactory() = default;

std::shared_ptr<const IKeywordExtractor>
KeywordExtractorFactory::make(vespalib::stringref input_field) const
{
    vespalib::hash_set<vespalib::string> indexes;
    indexes.insert(input_field);
    auto itr = _index_map.find(input_field);
    if (itr != _index_map.end()) {
        for (auto& index : itr->second) {
            indexes.insert(index);
        }
    }
    return std::make_shared<KeywordExtractor>(std::move(indexes));
}

}
