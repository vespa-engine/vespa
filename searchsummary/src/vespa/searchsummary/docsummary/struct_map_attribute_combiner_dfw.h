// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_combiner_dfw.h"

namespace search::attribute { class IAttributeContext; }

namespace search::docsummary {

class DocsumFieldWriterState;
class StructFieldsResolver;

/*
 * This class reads values from multiple struct field attributes and
 * inserts them as a map of struct.
 */
class StructMapAttributeCombinerDFW : public AttributeCombinerDFW
{
    std::string              _keyAttributeName;
    std::vector<std::string> _valueFields;
    std::vector<std::string> _valueAttributeNames;

    DocsumFieldWriterState* allocFieldWriterState(search::attribute::IAttributeContext &context, vespalib::Stash& stash, const MatchingElements* matching_elements) const override;
public:
    StructMapAttributeCombinerDFW(const std::string &fieldName,
                                  const StructFieldsResolver& fields_resolver,
                                  bool filter_elements,
                                  std::shared_ptr<MatchingElementsFields> matching_elems_fields);
    ~StructMapAttributeCombinerDFW() override;
};

}
