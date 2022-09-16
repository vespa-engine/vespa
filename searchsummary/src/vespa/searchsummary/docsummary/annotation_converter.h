// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_string_field_converter.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace document { class Span; }

namespace vespalib { class asciistream; }

namespace search::docsummary {

class IJuniperConverter;

/*
 * Class converting a string field value with annotations into a string
 * with interlinear annotations used by juniper before passing it to
 * the juniper converter.
 */
class AnnotationConverter : public IStringFieldConverter
{
    IJuniperConverter&     _juniper_converter;
    vespalib::stringref    _text;
    vespalib::asciistream  _out;

    template <typename ForwardIt>
    void handleAnnotations(const document::Span& span, ForwardIt it, ForwardIt last);
    template <typename ForwardIt>
    void annotateSpans(const document::Span& span, ForwardIt it, ForwardIt last);
    void handleIndexingTerms(const document::StringFieldValue& value);
public:
    AnnotationConverter(IJuniperConverter& juniper_converter);
    ~AnnotationConverter() override;
    void convert(const document::StringFieldValue &input, vespalib::slime::Inserter& inserter) override;
};

}
