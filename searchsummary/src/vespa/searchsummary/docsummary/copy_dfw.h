// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docsum_field_writer.h"

namespace search::docsummary {

class ResultConfig;

/*
 * Class for writing document summaries with content from another field. If the field is a multi-value field
 * then the selected_elements parameter to insert_field defines what elements to print.
 */
class CopyDFW : public DocsumFieldWriter
{
private:
    std::string _input_field_name;

public:
    explicit CopyDFW(const std::string& inputField);
    ~CopyDFW() override;

    bool isGenerated() const override { return false; }
    void insert_field(uint32_t docid, const IDocsumStoreDocument* doc, GetDocsumsState& state,
                      ElementIds selected_elements,
                      vespalib::slime::Inserter& target) const override;
};

}
