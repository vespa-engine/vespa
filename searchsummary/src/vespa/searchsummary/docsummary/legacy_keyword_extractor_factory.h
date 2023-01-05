// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_keyword_extractor_factory.h"

namespace search::docsummary {

/*
 * Class for creating an instance of IKeywordExtractor.
 */
class LegacyKeywordExtractorFactory : public IKeywordExtractorFactory
{
    std::shared_ptr<const IKeywordExtractor> _keyword_extractor;
public:
    explicit LegacyKeywordExtractorFactory(std::shared_ptr<const IKeywordExtractor> keyword_extractor);
    virtual ~LegacyKeywordExtractorFactory();
    std::shared_ptr<const IKeywordExtractor> make(vespalib::stringref) const override;
};

}
