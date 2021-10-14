// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "textextractordfw.h"
#include "tokenizer.h"
#include "docsumstate.h"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.textextractordfw");

namespace search::docsummary {

TextExtractorDFW::TextExtractorDFW() :
    _inputFieldEnum(-1)
{
}

bool
TextExtractorDFW::init(const vespalib::string & fieldName, const vespalib::string & inputField, const ResultConfig & config)
{
    _inputFieldEnum = config.GetFieldNameEnum().Lookup(inputField.c_str());
    if (_inputFieldEnum == -1) {
        LOG(warning, "Did not find input field '%s' as part of the docsum fields when initializing writer for field '%s'",
            inputField.c_str(), fieldName.c_str());
        return false;
    }
    return true;
}

void
TextExtractorDFW::insertField(uint32_t, GeneralResult *gres, GetDocsumsState *state, ResType,
                              vespalib::slime::Inserter &target)
{
    vespalib::string extracted;
    ResEntry * entry = gres->GetEntryFromEnumValue(_inputFieldEnum);
    if (entry != nullptr) {
        const char * buf = nullptr;
        uint32_t buflen = 0;
        entry->_resolve_field(&buf, &buflen, &state->_docSumFieldSpace);
        // extract the text
        Tokenizer tokenizer(buf, buflen);
        while (tokenizer.hasMoreTokens()) {
            Tokenizer::Token token = tokenizer.getNextToken();
            extracted.append(token.getText());
        }
    } else {
        LOG(warning, "Did not find input entry using field enum %d. Write an empty field", _inputFieldEnum);
    }
    target.insertString(vespalib::Memory(extracted.c_str(), extracted.size()));
}

}
