// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docsum_field_writer.h"

#include <memory>
#include <span>

namespace search::docsummary {

class ResultConfig;
class SlimeFillerFilter;

/*
 * Class for writing document summaries with content from another field. If the field is a multi-value field
 * then the selected_elements parameter to insert_field defines what elements to print.
 */
class CopyDFW : public DocsumFieldWriter {
private:
    std::string                        _input_field_name;
    std::unique_ptr<SlimeFillerFilter> _struct_fields_filter;

public:
    /**
     * @param struct_fields the struct sub-fields of the input field to include in the output, relative to
     *                      it, e.g. "value.s2a" for a map of struct; empty means all of them.
     */
    explicit CopyDFW(const std::string& inputField, std::span<const std::string> struct_fields = {});
    ~CopyDFW() override;

    bool isGenerated() const override { return false; }
    void insert_field(uint32_t docid, const IDocsumStoreDocument* doc, GetDocsumsState& state,
                      search::common::ElementIds selected_elements, vespalib::slime::Inserter& target) const override;
};

} // namespace search::docsummary
