// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tokens_converter.h"
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/text/utf8.h>
#include <vespa/vespalib/util/array.h>
#include <vespa/vsm/searcher/tokenizereader.h>

using document::StringFieldValue;
using search::byte;
using vespalib::Utf8Writer;
using vespalib::slime::ArrayInserter;
using vespalib::slime::Cursor;
using vespalib::slime::Inserter;
using vsm::TokenizeReader;

namespace vsm {

TokensConverter::TokensConverter(bool exact_match, search::Normalizing normalize_mode)
    : IStringFieldConverter(),
      _text(),
      _exact_match(exact_match),
      _normalize_mode(normalize_mode)
{
}

TokensConverter::~TokensConverter() = default;

void
TokensConverter::convert(const StringFieldValue &input, Inserter& inserter)
{
    _text = input.getValueRef();
    Cursor& a = inserter.insertArray();
    ArrayInserter ai(a);
    vespalib::Array<ucs4_t> buf(_text.size() + 1, 0);
    vespalib::string scratch;
    TokenizeReader reader(reinterpret_cast<const byte *> (_text.data()), _text.size(), buf.data());
    for (;;) {
        auto len = _exact_match ? reader.tokenize_exact_match(_normalize_mode) : reader.tokenize(_normalize_mode);
        if (len == 0) {
            break;
        }
        scratch.clear();
        Utf8Writer w(scratch);
        for (size_t i = 0; i < len; ++i) {
            w.putChar(buf[i]);
        }
        ai.insertString(scratch);
    }
}

bool
TokensConverter::render_weighted_set_as_array() const
{
    return true;
}

}
