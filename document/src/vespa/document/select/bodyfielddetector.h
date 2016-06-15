// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "traversingvisitor.h"

namespace document {
class DocumentTypeRepo;
class DocumentType;

namespace select {

class BodyFieldDetector : public TraversingVisitor
{
    const DocumentTypeRepo &_repo;

    void detectFieldType(const FieldValueNode *expr, const DocumentType &type);

public:
    BodyFieldDetector(const DocumentTypeRepo &repo)
        : _repo(repo), foundBodyField(false), foundHeaderField(false)
    {
    }

    bool foundBodyField;
    bool foundHeaderField;

    virtual void visitDocumentType(const DocType&) {
            // Need to deserialize header to know document type
        foundHeaderField = true;
    }

    void visitFieldValueNode(const FieldValueNode& expr);
};

class NeedDocumentDetector : public TraversingVisitor
{
private:
    bool _needDocument;
    virtual void visitDocumentType(const DocType &) {
        _needDocument = true;
    }
    virtual void visitFieldValueNode(const FieldValueNode &) {
        _needDocument = true;
    }
public:
    NeedDocumentDetector() : _needDocument(false) { }
    bool needDocument() const { return _needDocument; }
};

}  // namespace select
}  // namespace document

