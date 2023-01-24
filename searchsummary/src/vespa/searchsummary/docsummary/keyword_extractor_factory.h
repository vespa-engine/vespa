// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_keyword_extractor_factory.h"
#include <vespa/searchcommon/common/schema.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vector>

namespace search::index { class Schema; }

namespace search::docsummary {

/*
 * Class for creating an instance of IKeywordExtractor.
 */
class KeywordExtractorFactory : public IKeywordExtractorFactory
{
    vespalib::hash_map<vespalib::string, std::vector<vespalib::string>> _index_map;
public:
    KeywordExtractorFactory(const search::index::Schema& schema);
    ~KeywordExtractorFactory() override;
    std::shared_ptr<const IKeywordExtractor> make(vespalib::stringref input_field) const override;
};

}
