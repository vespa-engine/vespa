// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "keyword_extractor.h"
#include <vespa/vespalib/stllike/hash_set.hpp>

namespace search::docsummary {

KeywordExtractor::KeywordExtractor(StringSet indexes)
    : IKeywordExtractor(),
      _indexes(std::move(indexes))
{
}

KeywordExtractor::~KeywordExtractor() = default;

bool
KeywordExtractor::isLegalIndex(vespalib::stringref idx) const
{
    return _indexes.contains(idx);
}

}
