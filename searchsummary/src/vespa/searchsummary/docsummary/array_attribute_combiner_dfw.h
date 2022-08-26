// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_combiner_dfw.h"
#include <vector>

namespace search::attribute { class IAttributeContext; }

namespace search::docsummary {

class DocsumFieldWriterState;
class StructFieldsResolver;

/**
 * This class reads values from multiple struct field attributes and inserts them as an array of struct.
 *
 * Used to write both array of struct fields and map of primitives fields.
 */
class ArrayAttributeCombinerDFW : public AttributeCombinerDFW
{
    std::vector<vespalib::string> _fields;
    std::vector<vespalib::string> _attributeNames;
    bool                          _is_map_of_scalar;

    DocsumFieldWriterState* allocFieldWriterState(search::attribute::IAttributeContext &context, vespalib::Stash &stash, const MatchingElements* matching_elements) const override;
public:
    ArrayAttributeCombinerDFW(const vespalib::string &fieldName,
                              const StructFieldsResolver& fields_resolver,
                              bool filter_elements,
                              std::shared_ptr<MatchingElementsFields> matching_elems_fields);
    ~ArrayAttributeCombinerDFW() override;
};

}
