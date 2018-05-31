// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/fieldvalue/fieldvaluevisitor.h>
#include <vespa/document/fieldvalue/fieldvaluewriter.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/update/updatevisitor.h>

namespace vespalib {
    class nbostream;
}

namespace document {

class DocumentId;
class DocumentType;
class SerializableArray;
class ValueUpdate;
class FieldPathUpdate;

enum DocSerializationMode { COMPLETE, WITHOUT_BODY };

class VespaDocumentSerializer : private ConstFieldValueVisitor,
                                private UpdateVisitor,
                                public FieldValueWriter {
public:
    VespaDocumentSerializer(vespalib::nbostream &stream);

    static bool structNeedsReserialization(const StructFieldValue &value);

    void writeSerializedData(const void *buf, size_t length) override;
    void writeFieldValue(const FieldValue &value) override;

    void write(const FieldValue &value);

    void write(const DocumentId &value);
    void write(const DocumentType &value);
    void write(const Document &value, DocSerializationMode mode);
    void write(const AnnotationReferenceFieldValue &value);
    void write(const ArrayFieldValue &value);
    void write(const MapFieldValue &map);
    void write(const ByteFieldValue &value);
    void write(const DoubleFieldValue &val);
    void write(const FloatFieldValue &value);
    void write(const IntFieldValue &value);
    void write(const LongFieldValue &value);
    void write(const PredicateFieldValue &value);
    void write(const RawFieldValue &value);
    void write(const ShortFieldValue &value);
    void write(const StringFieldValue &val);
    void write(const StructFieldValue &val, const FieldSet& fieldSet);
    void write(const WeightedSetFieldValue &value);
    void write(const TensorFieldValue &value);
    void write(const ReferenceFieldValue& value);

    void write42(const DocumentUpdate &value);
    void writeHEAD(const DocumentUpdate &value);
    void write(const FieldUpdate &value);
    void write(const ValueUpdate &value);
    static uint16_t getCurrentVersion() { return serialize_version; }
private:
    static constexpr int serialize_version = 8;
    void writeUnchanged(const SerializableArray &val);
    uint8_t getContentCode(bool hasHeader, bool hasBody) const;

    void write(const FieldPathUpdate &value);

    void write(const RemoveValueUpdate &value);
    void write(const AddValueUpdate &value);
    void write(const ArithmeticValueUpdate &value);
    void write(const AssignValueUpdate &value);
    void write(const ClearValueUpdate &value);
    void write(const MapValueUpdate &value);
    void write(const AddFieldPathUpdate &value);
    void write(const AssignFieldPathUpdate &value);
    void write(const RemoveFieldPathUpdate &value);

    void visit(const DocumentUpdate &value)        override { writeHEAD(value); }
    void visit(const FieldUpdate &value)           override { write(value); }
    void visit(const RemoveValueUpdate &value)     override { write(value); }
    void visit(const AddValueUpdate &value)        override { write(value); }
    void visit(const ArithmeticValueUpdate &value) override { write(value); }
    void visit(const AssignValueUpdate &value)     override { write(value); }
    void visit(const ClearValueUpdate &value)      override { write(value); }
    void visit(const MapValueUpdate &value)        override { write(value); }
    void visit(const AddFieldPathUpdate &value)    override { write(value); }
    void visit(const AssignFieldPathUpdate &value) override { write(value); }
    void visit(const RemoveFieldPathUpdate &value) override { write(value); }

    void visit(const AnnotationReferenceFieldValue &value) override { write(value); }
    void visit(const ArrayFieldValue &value)               override { write(value); }
    void visit(const ByteFieldValue &value)                override { write(value); }
    void visit(const Document &value)                      override { write(value, COMPLETE); }
    void visit(const DoubleFieldValue &value)              override { write(value); }
    void visit(const FloatFieldValue &value)               override { write(value); }
    void visit(const IntFieldValue &value)                 override { write(value); }
    void visit(const LongFieldValue &value)                override { write(value); }
    void visit(const MapFieldValue &value)                 override { write(value); }
    void visit(const PredicateFieldValue &value)           override { write(value); }
    void visit(const RawFieldValue &value)                 override { write(value); }
    void visit(const ShortFieldValue &value)               override { write(value); }
    void visit(const StringFieldValue &value)              override { write(value); }
    void visit(const StructFieldValue &value)              override;
    void visit(const WeightedSetFieldValue &value)         override { write(value); }
    void visit(const TensorFieldValue &value)              override { write(value); }
    void visit(const ReferenceFieldValue& value)           override { write(value); }

    vespalib::nbostream &_stream;
};
}  // namespace document

