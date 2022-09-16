// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/fieldvalue/fieldvaluevisitor.h>
#include <cstdint>
#include <vector>

namespace vespalib::slime { struct Inserter; }

namespace search::docsummary {

class IStringFieldConverter;

/*
 * Class inserting a field value into a slime object.
 */
class SlimeFiller : public document::ConstFieldValueVisitor {

    vespalib::slime::Inserter&   _inserter;
    const std::vector<uint32_t>* _matching_elems;
    IStringFieldConverter*       _string_converter;

    bool filter_matching_elements() const {
        return _matching_elems != nullptr;
    }

    template <typename Value>
    bool empty_or_empty_after_filtering(const Value& value) const {
        return (value.isEmpty() || (filter_matching_elements() && (_matching_elems->empty() || _matching_elems->back() >= value.size())));
    }

    void visit(const document::AnnotationReferenceFieldValue& v) override;
    void visit(const document::Document& v) override;
    void visit(const document::MapFieldValue& v) override;
    void visit(const document::ArrayFieldValue& value) override;
    void visit(const document::StringFieldValue& value) override;
    void visit(const document::IntFieldValue& value) override;
    void visit(const document::LongFieldValue& value) override;
    void visit(const document::ShortFieldValue& value) override;
    void visit(const document::ByteFieldValue& value) override;
    void visit(const document::BoolFieldValue& value) override;
    void visit(const document::DoubleFieldValue& value) override;
    void visit(const document::FloatFieldValue& value) override;
    void visit(const document::PredicateFieldValue& value) override;
    void visit(const document::RawFieldValue& value) override;
    void visit(const document::StructFieldValue& value) override;
    void visit(const document::WeightedSetFieldValue& value) override;
    void visit(const document::TensorFieldValue& value) override;
    void visit(const document::ReferenceFieldValue& value) override;
public:
    SlimeFiller(vespalib::slime::Inserter& inserter);
    SlimeFiller(vespalib::slime::Inserter& inserter, const std::vector<uint32_t>* matching_elems);
    SlimeFiller(vespalib::slime::Inserter& inserter, IStringFieldConverter* string_converter);
    ~SlimeFiller() override;
};

}
