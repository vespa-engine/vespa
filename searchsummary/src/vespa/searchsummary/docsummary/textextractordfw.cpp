// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tokenizer.h"
#include "textextractordfw.h"
#include "docsumstate.h"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.textextractordfw");

namespace search {
namespace docsummary {

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
TextExtractorDFW::insertField(uint32_t,
                              GeneralResult *gres,
                              GetDocsumsState *state,
                              ResType,
                              vespalib::slime::Inserter &target)
{
    vespalib::string extracted;
    ResEntry * entry = gres->GetEntryFromEnumValue(_inputFieldEnum);
    if (entry != NULL) {
        const char * buf = NULL;
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

uint32_t
TextExtractorDFW::WriteField(uint32_t docid,
                             GeneralResult * gres,
                             GetDocsumsState * state,
                             ResType type,
                             search::RawBuf * target)
{
    (void) docid;
    (void) type;
    uint32_t slen = 0;
    uint32_t begin = target->GetUsedLen();
    // write text length
    target->append(&slen, sizeof(slen));

    ResEntry * entry = gres->GetEntryFromEnumValue(_inputFieldEnum);
    if (entry != NULL) {
        const char * buf = NULL;
        uint32_t buflen = 0;
        entry->_resolve_field(&buf, &buflen, &state->_docSumFieldSpace);
        // extract the text
        Tokenizer tokenizer(buf, buflen);
        while (tokenizer.hasMoreTokens()) {
            Tokenizer::Token token = tokenizer.getNextToken();
            target->append(token.getText().c_str(), token.getText().size());
        }
    } else {
        LOG(warning, "Did not find input entry using field enum %d. Write an empty field", _inputFieldEnum);
    }

    // calculate number of bytes written
    uint32_t written = target->GetUsedLen() - begin;
    // patch in correct text length
    slen = written - sizeof(slen);
    memcpy(target->GetWritableDrainPos(begin), &slen, sizeof(slen));

    return written;
}

}
}

