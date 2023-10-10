// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vector>

namespace document {

class FieldValue;
class StringFieldValue;
class Span;

}

namespace search::linguistics {

/*
 * Class used to extract tokens from annotated string field value.
 */
class TokenExtractor {
public:
    using SpanTerm = std::pair<document::Span, const document::FieldValue*>;
    static bool extract(bool allow_zero_length_tokens, std::vector<SpanTerm>& terms, const document::StringFieldValue::SpanTrees& trees);
};

}
