// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/vespalib/stllike/string.h>

namespace search { class MatchingElementsFields; }

namespace search::docsummary {

class DocsumFieldWriter;

/*
 * Factory interface class for creating docsum field writers.
 */
class IDocsumFieldWriterFactory
{
public:
    virtual ~IDocsumFieldWriterFactory() = default;
    /**
     * Implementations can throw vespalib::IllegalArgumentException if setup of field writer fails.
     */
    virtual std::unique_ptr<DocsumFieldWriter> create_docsum_field_writer(const vespalib::string& field_name,
                                                                          const vespalib::string& command,
                                                                          const vespalib::string& source,
                                                                          std::shared_ptr<MatchingElementsFields> matching_elems_fields) = 0;
};

}
