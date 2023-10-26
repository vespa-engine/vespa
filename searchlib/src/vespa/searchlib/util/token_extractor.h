// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/annotation/span.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace document {

class Document;
class Span;
class StringFieldValue;

}

namespace search::linguistics {

/*
 * Class used to extract tokens from annotated string field value.
 */
class TokenExtractor {
    const vespalib::string& _field_name;
    size_t                  _max_word_len;

public:
    struct SpanTerm {
        document::Span      span;
        vespalib::stringref word;
        bool                altered;

        SpanTerm(const document::Span& span_, vespalib::stringref word_, bool altered_) noexcept
            : span(span_),
              word(word_),
              altered(altered_)
        {
        }
        SpanTerm() noexcept
            : span(),
              word(),
              altered(false)
        {
        }
        bool operator<(const SpanTerm& rhs) const noexcept {
            if (span != rhs.span) {
                return span < rhs.span;
            }
            return word < rhs.word;
        }
    };

private:
    void consider_word(std::vector<SpanTerm>& terms, vespalib::stringref text, const document::Span& span, const document::FieldValue* fv, const document::Document* doc) const;

public:
    TokenExtractor(const vespalib::string& field_name, size_t max_word_len);
    ~TokenExtractor();
    void extract(std::vector<SpanTerm>& terms, const document::StringFieldValue::SpanTrees& trees, vespalib::stringref text, const document::Document* doc) const;
    vespalib::stringref sanitize_word(vespalib::stringref word, const document::Document* doc) const;
};

}
