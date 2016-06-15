// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/expressionnode.h>
#include <vespa/document/document.h>
#include <vespa/vespalib/objects/objectoperation.h>
#include <vespa/vespalib/objects/objectpredicate.h>

namespace search {
namespace expression {

class DocumentAccessorNode : public ExpressionNode
{
public:
    DECLARE_ABSTRACT_EXPRESSIONNODE(DocumentAccessorNode);
    class Configure : public vespalib::ObjectOperation, public vespalib::ObjectPredicate
    {
    public:
        Configure(const document::DocumentType & documentType) : _docType(documentType) { }
    private:
        virtual void execute(vespalib::Identifiable &obj) { static_cast<DocumentAccessorNode &>(obj).setDocType(_docType); }
        virtual bool check(const vespalib::Identifiable &obj) const { return obj.inherits(DocumentAccessorNode::classId); }
        const document::DocumentType & _docType;
    };

    void setDoc(const document::Document & doc) { onDoc(doc); }
    void setDocType(const document::DocumentType & docType) { onDocType(docType); }
    virtual const vespalib::string & getFieldName() const { return _S_docId; }
private:
    virtual void onDoc(const document::Document & doc) = 0;
    virtual void onDocType(const document::DocumentType & docType) = 0;
    static const vespalib::string _S_docId;
};

}
}

