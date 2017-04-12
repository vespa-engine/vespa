// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchsummary/docsummary/docsumfieldwriter.h>

namespace search {
namespace docsummary {

/**
 * This is the docsum field writer used to extract the original text from a disk summary on the juniper format.
 **/
class TextExtractorDFW : public IDocsumFieldWriter
{
private:
    TextExtractorDFW(const TextExtractorDFW &);
    TextExtractorDFW & operator=(const TextExtractorDFW &);

    int _inputFieldEnum;

public:
    TextExtractorDFW();
    virtual ~TextExtractorDFW() {}
    bool init(const vespalib::string & fieldName, const vespalib::string & inputField, const ResultConfig & config);
    // Inherit doc
    virtual bool IsGenerated() const override { return false; }
    // Inherit doc
    virtual void insertField(uint32_t docid,
                             GeneralResult *gres,
                             GetDocsumsState *state,
                             ResType type,
                             vespalib::slime::Inserter &target) override;
};

}
}

