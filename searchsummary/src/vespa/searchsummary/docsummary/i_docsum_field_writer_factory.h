// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <string>

namespace search::docsummary {

class DocsumFieldWriter;
class SummaryElementsSelector;

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
    virtual std::unique_ptr<DocsumFieldWriter> create_docsum_field_writer(const std::string& field_name,
                                                                          const SummaryElementsSelector& elements_selector,
                                                                          const std::string& command,
                                                                          const std::string& source) = 0;
};

}
