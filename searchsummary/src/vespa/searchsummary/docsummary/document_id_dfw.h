// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docsum_field_writer.h"

namespace search::docsummary {

/*
 * Class for writing document id field.
 */
class DocumentIdDFW : public DocsumFieldWriter
{
private:
public:
    DocumentIdDFW();
    ~DocumentIdDFW() override;
    bool isGenerated() const override { return false; }
    void insert_field(uint32_t docid, const IDocsumStoreDocument* doc, GetDocsumsState& state,
                      ElementIds selected_elements,
                      vespalib::slime::Inserter &target) const override;
};

}
