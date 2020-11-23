// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/fieldvalue/fieldvaluevisitor.h>
#include <vespa/document/repo/fixedtyperepo.h>
#include <memory>

namespace vespalib { class nbostream; }
namespace vespalib::eval { struct Value; }

namespace document {
class DocumentId;
class DocumentType;
class DocumentTypeRepo;
class FieldValue;

class VespaDocumentDeserializer : private FieldValueVisitor {
    vespalib::nbostream &_stream;
    FixedTypeRepo _repo;
    uint16_t _version;

    void visit(AnnotationReferenceFieldValue &value) override { read(value); }
    void visit(ArrayFieldValue &value) override { read(value); }
    void visit(BoolFieldValue &value) override { read(value); }
    void visit(ByteFieldValue &value) override { read(value); }
    void visit(Document &value) override { read(value); }
    void visit(DoubleFieldValue &value) override { read(value); }
    void visit(FloatFieldValue &value) override { read(value); }
    void visit(IntFieldValue &value) override { read(value); }
    void visit(LongFieldValue &value) override { read(value); }
    void visit(MapFieldValue &value) override { read(value); }
    void visit(PredicateFieldValue &value) override { read(value); }
    void visit(RawFieldValue &value) override { read(value); }
    void visit(ShortFieldValue &value) override { read(value); }
    void visit(StringFieldValue &value) override { read(value); }
    void visit(StructFieldValue &value) override { read(value); }
    void visit(WeightedSetFieldValue &value) override { read(value); }
    void visit(TensorFieldValue &value) override { read(value); }
    void visit(ReferenceFieldValue &value) override { read(value); }

    void readDocument(Document &value);

public:
    VespaDocumentDeserializer(const DocumentTypeRepo &repo, vespalib::nbostream &stream, uint16_t version) :
        _stream(stream),
        _repo(repo),
        _version(version)
    { }

    VespaDocumentDeserializer(const FixedTypeRepo &repo, vespalib::nbostream &stream, uint16_t version) :
        _stream(stream),
        _repo(repo),
        _version(version)
    { }

    // returns NULL if the read doc type equals guess.
    const DocumentType *readDocType(const DocumentType &guess);

    void read(FieldValue &value);

    void read(DocumentId &value);
    void read(DocumentType &value);
    void read(Document &value);
    void read(AnnotationReferenceFieldValue &value);
    void read(ArrayFieldValue &value);
    void read(MapFieldValue &value);
    void read(BoolFieldValue &value);
    void read(ByteFieldValue &value);
    void read(DoubleFieldValue &value);
    void read(FloatFieldValue &value);
    void read(IntFieldValue &value);
    void read(LongFieldValue &value);
    void read(PredicateFieldValue &value);
    void read(RawFieldValue &value);
    void read(ShortFieldValue &value);
    void read(StringFieldValue &value);
    void read(StructFieldValue &value);
    void readStructNoReset(StructFieldValue &value);
    void read(WeightedSetFieldValue &value);
    void read(TensorFieldValue &value);
    std::unique_ptr<vespalib::eval::Value> readTensor();
    void read(ReferenceFieldValue& value);
};
}  // namespace document

