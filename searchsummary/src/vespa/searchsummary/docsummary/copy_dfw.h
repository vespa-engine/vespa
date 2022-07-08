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
    uint32_t _inputFieldEnumValue;
    vespalib::string _input_field_name;

public:
    CopyDFW();
    ~CopyDFW() override;

    bool Init(const ResultConfig & config, const char *inputField);

    bool IsGenerated() const override { return false; }
    void insertField(uint32_t docid, GeneralResult *gres, GetDocsumsState *state, ResType type,
                     vespalib::slime::Inserter &target) override;
};

}
