// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace document { class StringFieldValue; }
namespace vespalib::slime { struct Inserter; }

namespace search::docsummary {

/**
 * Interface class for inserting a dynamic string.
 */
class IStringFieldConverter
{
public:
    virtual ~IStringFieldConverter() = default;
    virtual void convert(const document::StringFieldValue &input, vespalib::slime::Inserter& inserter) = 0;
};

}
