// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_keyword_extractor.h"
#include <vespa/vespalib/stllike/hash_set.h>

namespace search::docsummary {

/*
 * Class for checking if query term index name indicates that
 * related query term is useful from the perspective of juniper.
 */
class KeywordExtractor : public IKeywordExtractor
{
    using StringSet = vespalib::hash_set<vespalib::string>;
    StringSet _indexes;
public:
    KeywordExtractor(StringSet indexes);
    ~KeywordExtractor() override;
    bool isLegalIndex(vespalib::stringref idx) const override;
};

}
