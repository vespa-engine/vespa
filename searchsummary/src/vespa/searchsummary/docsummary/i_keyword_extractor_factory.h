// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace search::docsummary {

class IKeywordExtractor;

/*
 * Interface class for creating an instance of IKeywordExtractor for a
 * specific input field.
 */
class IKeywordExtractorFactory
{
public:
    virtual ~IKeywordExtractorFactory() = default;

    virtual std::shared_ptr<const IKeywordExtractor> make(vespalib::stringref input_field) const = 0;
};

}
