// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docsum_field_writer.h"
#include <memory>
#include <vector>

namespace search { class MatchingElementsFields; }

namespace search::attribute { class IAttributeContext; }

namespace search::docsummary {

/**
 * Field writer that filters matched elements (according to the query) from a multi-value or complex field
 * (array of primitive, weighted set of primitive, map of primitives, map of struct, array of struct)
 * that is retrieved from the document store.
 */
class MatchedElementsFilterDFW : public DocsumFieldWriter {
private:
    std::string _input_field_name;

    const std::vector<uint32_t>& get_matching_elements(uint32_t docid, GetDocsumsState& state) const;

public:
    MatchedElementsFilterDFW(const std::string& input_field_name);
    static std::unique_ptr<DocsumFieldWriter> create(const std::string& input_field_name);
    ~MatchedElementsFilterDFW() override;
    bool isGenerated() const override { return false; }
    void insert_field(uint32_t docid, const IDocsumStoreDocument* doc, GetDocsumsState& state,
                      const SummaryElementsSelector& elements_selector,
                      vespalib::slime::Inserter& target) const override;
};

}
