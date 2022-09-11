// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace document
{
class Span;
class StringFieldValue;
}

namespace vespalib { class asciistream; }

namespace search::docsummary {

/*
 * Class converting a string field value with annotations into a string
 * with interlinear annotations used by juniper.
 */
struct AnnotationConverter {
    const vespalib::string text;
    vespalib::asciistream& out;

    template <typename ForwardIt>
    void handleAnnotations(const document::Span& span, ForwardIt it, ForwardIt last);
    template <typename ForwardIt>
    void annotateSpans(const document::Span& span, ForwardIt it, ForwardIt last);
public:
    AnnotationConverter(const vespalib::string& s, vespalib::asciistream& stream)
        : text(s), out(stream) {}
    void handleIndexingTerms(const document::StringFieldValue& value);
};

}
