// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/vespalib/stllike/string.h>

namespace search::docsummary {

class DocsumFieldWriter;

/*
 * Factory interface class for creating docsum field writers.
 */
class IDocsumFieldWriterFactory
{
public:
    virtual ~IDocsumFieldWriterFactory() = default;
    virtual std::unique_ptr<DocsumFieldWriter> create_docsum_field_writer(const vespalib::string& fieldName, const vespalib::string& overrideName, const vespalib::string& argument, bool& rc) = 0;
};

}
