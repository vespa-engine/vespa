// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docsum_field_writer.h"

namespace search::docsummary {

/*
 * Class for writing values from a string attribute vector as arrays
 * containing the tokens. The string values are not split but they are
 * lowercased if the string attribute vector uses uncased matching.
 */
class AttributeTokensDFW : public DocsumFieldWriter
{
private:
    vespalib::string            _input_field_name;
    uint32_t _state_index; // index into _fieldWriterStates in GetDocsumsState

protected:
    const vespalib::string & getAttributeName() const override;
public:
    AttributeTokensDFW(const vespalib::string& input_field_name);
    ~AttributeTokensDFW() override;
    bool isGenerated() const override;
    bool setFieldWriterStateIndex(uint32_t fieldWriterStateIndex) override;
    void insertField(uint32_t docid, const IDocsumStoreDocument* doc, GetDocsumsState& state, vespalib::slime::Inserter& target) const override;
};

}
