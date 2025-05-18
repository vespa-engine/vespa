// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docsum_field_writer.h"
#include <memory>

namespace search::attribute { class IAttributeContext; }

namespace search::docsummary {

class DocsumFieldWriterState;
class DynamicDocsumWriter;

/*
 * This class reads values from multiple struct field attributes and
 * inserts them as an array of struct or a map of struct.
 */
class AttributeCombinerDFW : public DocsumFieldWriter
{
protected:
    uint32_t _stateIndex;
    AttributeCombinerDFW();
protected:
    virtual DocsumFieldWriterState* allocFieldWriterState(search::attribute::IAttributeContext& context,
                                                          GetDocsumsState& state,
                                                          const SummaryElementsSelector& elements_selector) const = 0;
public:
    ~AttributeCombinerDFW() override;
    bool isGenerated() const override { return true; }
    bool setFieldWriterStateIndex(uint32_t fieldWriterStateIndex) override;
    static std::unique_ptr<DocsumFieldWriter> create(const std::string &fieldName,
                                                     search::attribute::IAttributeContext &attrCtx);
    void insert_field(uint32_t docid, const IDocsumStoreDocument* doc, GetDocsumsState& state,
                      const SummaryElementsSelector& elements_selector,
                      vespalib::slime::Inserter &target) const override;
};

}

