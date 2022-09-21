// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docsum_field_writer.h"

namespace search::docsummary {

class ResultConfig;

/*
 * Class for writing document summaries with content from another field.
 */
class CopyDFW : public DocsumFieldWriter
{
private:
    vespalib::string _input_field_name;

public:
    explicit CopyDFW(const vespalib::string& inputField);
    ~CopyDFW() override;

    bool isGenerated() const override { return false; }
    void insertField(uint32_t docid, const IDocsumStoreDocument* doc, GetDocsumsState& state, vespalib::slime::Inserter &target) const override;
};

}
