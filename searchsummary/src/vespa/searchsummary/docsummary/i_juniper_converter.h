// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib::slime { struct Inserter; }

namespace search::docsummary {

/**
 * Interface class for inserting a dynamic string based on an
 * annotated full string and query context.
 */
class IJuniperConverter
{
public:
    virtual ~IJuniperConverter() = default;
    virtual void insert_juniper_field(vespalib::stringref input, vespalib::slime::Inserter& inserter) = 0;
};

}
