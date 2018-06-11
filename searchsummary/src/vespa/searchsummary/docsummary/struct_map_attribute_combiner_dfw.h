// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_combiner_dfw.h"

namespace search::attribute { class IAttributeContext; }

namespace search::docsummary {

class DocsumFieldWriterState;

/*
 * This class reads values from multiple struct field attributes and
 * inserts them as a map of struct.
 */
class StructMapAttributeCombinerDFW : public AttributeCombinerDFW
{
    vespalib::string              _keyAttributeName;
    std::vector<vespalib::string> _valueFields;
    std::vector<vespalib::string> _valueAttributeNames;

    std::unique_ptr<DocsumFieldWriterState> allocFieldWriterState(search::attribute::IAttributeContext &context) override;
public:
    StructMapAttributeCombinerDFW(const vespalib::string &fieldName,
                              const std::vector<vespalib::string> &valueFields);
    ~StructMapAttributeCombinerDFW() override;
};

}
