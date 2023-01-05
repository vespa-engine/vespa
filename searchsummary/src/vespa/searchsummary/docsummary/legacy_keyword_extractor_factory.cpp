// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "legacy_keyword_extractor_factory.h"

namespace search::docsummary {

LegacyKeywordExtractorFactory::LegacyKeywordExtractorFactory(std::shared_ptr<const IKeywordExtractor> keyword_extractor)
    : IKeywordExtractorFactory(),
      _keyword_extractor(std::move(keyword_extractor))
{
}

LegacyKeywordExtractorFactory::~LegacyKeywordExtractorFactory() = default;

std::shared_ptr<const IKeywordExtractor>
LegacyKeywordExtractorFactory::make(vespalib::stringref) const
{
    return _keyword_extractor;
}

}
