// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/document/repo/fixedtyperepo.h>
#include <memory>

namespace document {
class SpanList;
struct SpanNode;
class SpanTree;
class StringFieldValue;
}

namespace search::test {

class DocBuilder;

/*
 * Helper class to build annotated string field.
 */
class StringFieldBuilder {
    vespalib::string    _value;
    size_t              _span_start;
    document::SpanList* _span_list;  // owned by _span_tree
    std::unique_ptr<document::SpanTree> _span_tree;
    const document::SpanNode* _last_span;
    const document::FixedTypeRepo _repo;
    void start_annotate();
    void add_span();
public:
    StringFieldBuilder(const DocBuilder& doc_builder);
    ~StringFieldBuilder();
    StringFieldBuilder& token(const vespalib::string& val, bool is_word);
    StringFieldBuilder& word(const vespalib::string& val) { return token(val, true); }
    StringFieldBuilder& space() { return token(" ", false); }
    StringFieldBuilder& tokenize(const vespalib::string& val);
    StringFieldBuilder& alt_word(const vespalib::string& val);
    document::StringFieldValue build();
};

}
