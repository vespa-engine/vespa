// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace document {
class AnnotationReferenceFieldValue;
class ArrayFieldValue;
class BoolFieldValue;
class ByteFieldValue;
class Document;
class DoubleFieldValue;
class FloatFieldValue;
class IntFieldValue;
class LongFieldValue;
class MapFieldValue;
class PredicateFieldValue;
class RawFieldValue;
class ShortFieldValue;
class StringFieldValue;
class StructFieldValue;
class WeightedSetFieldValue;
class TensorFieldValue;
class ReferenceFieldValue;

struct FieldValueVisitor {
    virtual ~FieldValueVisitor() {}

    virtual void visit(AnnotationReferenceFieldValue &value) = 0;
    virtual void visit(ArrayFieldValue &value) = 0;
    virtual void visit(BoolFieldValue &value) = 0;
    virtual void visit(ByteFieldValue &value) = 0;
    virtual void visit(Document &value) = 0;
    virtual void visit(DoubleFieldValue &value) = 0;
    virtual void visit(FloatFieldValue &value) = 0;
    virtual void visit(IntFieldValue &value) = 0;
    virtual void visit(LongFieldValue &value) = 0;
    virtual void visit(MapFieldValue &value) = 0;
    virtual void visit(PredicateFieldValue &value) = 0;
    virtual void visit(RawFieldValue &value) = 0;
    virtual void visit(ShortFieldValue &value) = 0;
    virtual void visit(StringFieldValue &value) = 0;
    virtual void visit(StructFieldValue &value) = 0;
    virtual void visit(WeightedSetFieldValue &value) = 0;
    virtual void visit(TensorFieldValue &value) = 0;
    virtual void visit(ReferenceFieldValue& value) = 0;
};

struct ConstFieldValueVisitor {
    virtual ~ConstFieldValueVisitor() {}

    virtual void visit(const AnnotationReferenceFieldValue &value) = 0;
    virtual void visit(const ArrayFieldValue &value) = 0;
    virtual void visit(const BoolFieldValue &value) = 0;
    virtual void visit(const ByteFieldValue &value) = 0;
    virtual void visit(const Document &value) = 0;
    virtual void visit(const DoubleFieldValue &value) = 0;
    virtual void visit(const FloatFieldValue &value) = 0;
    virtual void visit(const IntFieldValue &value) = 0;
    virtual void visit(const LongFieldValue &value) = 0;
    virtual void visit(const MapFieldValue &value) = 0;
    virtual void visit(const PredicateFieldValue &value) = 0;
    virtual void visit(const RawFieldValue &value) = 0;
    virtual void visit(const ShortFieldValue &value) = 0;
    virtual void visit(const StringFieldValue &value) = 0;
    virtual void visit(const StructFieldValue &value) = 0;
    virtual void visit(const WeightedSetFieldValue &value) = 0;
    virtual void visit(const TensorFieldValue &value) = 0;
    virtual void visit(const ReferenceFieldValue& value) = 0;
};

}  // namespace document

