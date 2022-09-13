// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace document { class StringFieldValue; }
namespace vespalib::slime { struct Inserter; }

namespace search::docsummary {

/**
 * Interface class for inserting a dynamic string based on an
 * annotated full string and query context.
 *
 * For streaming search we use the same interface in an adapter that
 * calls a snippet modifier (vsm::SnippetModifier) to add the annotation
 * needed by juniper.
 */
class IJuniperConverter
{
public:
    virtual ~IJuniperConverter() = default;
    virtual void insert_juniper_field(vespalib::stringref input, vespalib::slime::Inserter& inserter) = 0;
    virtual void insert_juniper_field(const document::StringFieldValue &input, vespalib::slime::Inserter& inserter) = 0;
};

}
