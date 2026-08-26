// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "combiner_shape.h"

#include <memory>
#include <span>
#include <string>

namespace search::docsummary {

class DocsumFieldWriter;

/*
 * Factory interface class for creating docsum field writers.
 */
class IDocsumFieldWriterFactory {
public:
    virtual ~IDocsumFieldWriterFactory() = default;
    /**
     * Implementations can throw vespalib::IllegalArgumentException if setup of field writer fails.
     *
     * @param struct_fields the names of the struct sub-fields to include in the output, used by the
     *                      "attributecombiner" command when the source field is an array of struct, or a
     *                      map, and only some of the sub-fields should be part of the summary. For a map the
     *                      sub-fields are "key" and "value", where a value of struct type has its own
     *                      sub-fields named "value.<name>". An empty list means all sub-fields are included.
     * @param declared_shape which shape the "attributecombiner" command should write for the source field,
     *                      or CombinerShape::INFER to have it deduced from the struct field attributes.
     */
    virtual std::unique_ptr<DocsumFieldWriter>
    create_docsum_field_writer(const std::string& field_name, const std::string& command, const std::string& source,
                               std::span<const std::string> struct_fields, CombinerShape declared_shape) = 0;
};

} // namespace search::docsummary
