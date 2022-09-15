// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_juniper_converter.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace document { class Span; }

namespace vespalib { class asciistream; }

namespace search::docsummary {

/*
 * Class converting a string field value with annotations into a string
 * with interlinear annotations used by juniper before passing it to
 * the original juniper converter.
 */
class AnnotationConverter : public IJuniperConverter
{
    IJuniperConverter&     _orig_converter;
    vespalib::stringref    _text;
    vespalib::asciistream  _out;

    template <typename ForwardIt>
    void handleAnnotations(const document::Span& span, ForwardIt it, ForwardIt last);
    template <typename ForwardIt>
    void annotateSpans(const document::Span& span, ForwardIt it, ForwardIt last);
    void handleIndexingTerms(const document::StringFieldValue& value);
public:
    AnnotationConverter(IJuniperConverter& orig_converter);
    ~AnnotationConverter() override;
    void insert_juniper_field(vespalib::stringref input, vespalib::slime::Inserter& inserter) override;
    void insert_juniper_field(const document::StringFieldValue &input, vespalib::slime::Inserter& inserter) override;
};

}
