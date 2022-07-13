// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/fieldvalue/fieldvaluevisitor.h>

namespace search::docsummary {

/*
 * This class checks if field value is considered the same value as
 * undefined values string/double/float attribute vectors or empty
 * array/map/weighted set.
 */
class CheckUndefinedValueVisitor : public document::ConstFieldValueVisitor
{
    bool _is_undefined;
    void visit(const document::AnnotationReferenceFieldValue&) override;
    void visit(const document::ArrayFieldValue& value) override;
    void visit(const document::BoolFieldValue&) override;
    void visit(const document::ByteFieldValue&) override;
    void visit(const document::Document&) override;
    void visit(const document::DoubleFieldValue& value) override;
    void visit(const document::FloatFieldValue& value) override;
    void visit(const document::IntFieldValue&) override;
    void visit(const document::LongFieldValue&) override;
    void visit(const document::MapFieldValue& value) override;
    void visit(const document::PredicateFieldValue&) override;
    void visit(const document::RawFieldValue& value) override;
    void visit(const document::ShortFieldValue&) override;
    void visit(const document::StringFieldValue& value) override;
    void visit(const document::StructFieldValue&) override;
    void visit(const document::WeightedSetFieldValue& value) override;
    void visit(const document::TensorFieldValue&) override;
    void visit(const document::ReferenceFieldValue&) override;
public:
    CheckUndefinedValueVisitor();
    ~CheckUndefinedValueVisitor() override;
    bool is_undefined() const noexcept { return _is_undefined; }
};

}
