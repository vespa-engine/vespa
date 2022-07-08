// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docsum_field_writer.h"

namespace search::docsummary {

class ResultConfig;

/**
 * This is the docsum field writer used to extract the original text from a disk summary on the juniper format.
 **/
class TextExtractorDFW : public DocsumFieldWriter
{
private:
    TextExtractorDFW(const TextExtractorDFW &);
    TextExtractorDFW & operator=(const TextExtractorDFW &);

    int _inputFieldEnum;

public:
    TextExtractorDFW();
    ~TextExtractorDFW() override = default;
    bool init(const vespalib::string & fieldName, const vespalib::string & inputField, const ResultConfig & config);
    bool IsGenerated() const override { return false; }
    void insertField(uint32_t docid, GeneralResult *gres, GetDocsumsState *state,
                     ResType type, vespalib::slime::Inserter &target) override;
};

}
