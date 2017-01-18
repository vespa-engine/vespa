// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/document/fieldvalue/fieldvaluevisitor.h>
#include <vespa/document/repo/fixedtyperepo.h>

namespace vespalib { class nbostream; }

namespace document {
class DocumentId;
class DocumentType;
class DocumentTypeRepo;
class FieldValue;

class VespaDocumentDeserializer : private FieldValueVisitor {
    vespalib::nbostream &_stream;
    FixedTypeRepo _repo;
    uint16_t _version;

    virtual void visit(AnnotationReferenceFieldValue &value) { read(value); }
    virtual void visit(ArrayFieldValue &value) { read(value); }
    virtual void visit(ByteFieldValue &value) { read(value); }
    virtual void visit(Document &value) { read(value); }
    virtual void visit(DoubleFieldValue &value) { read(value); }
    virtual void visit(FloatFieldValue &value) { read(value); }
    virtual void visit(IntFieldValue &value) { read(value); }
    virtual void visit(LongFieldValue &value) { read(value); }
    virtual void visit(MapFieldValue &value) { read(value); }
    virtual void visit(PredicateFieldValue &value) { read(value); }
    virtual void visit(RawFieldValue &value) { read(value); }
    virtual void visit(ShortFieldValue &value) { read(value); }
    virtual void visit(StringFieldValue &value) { read(value); }
    virtual void visit(StructFieldValue &value) { read(value); }
    virtual void visit(WeightedSetFieldValue &value) { read(value); }
    virtual void visit(TensorFieldValue &value) { read(value); }

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
};
}  // namespace document

