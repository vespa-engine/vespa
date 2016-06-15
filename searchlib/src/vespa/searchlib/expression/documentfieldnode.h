// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/documentaccessornode.h>
#include <vespa/searchlib/expression/resultnode.h>
#include <vespa/searchlib/expression/resultvector.h>
#include <vespa/document/document.h>
#include <vespa/vespalib/encoding/base64.h>

namespace search {
namespace expression {

class DefaultValue : public ResultNode
{
public:
    DECLARE_EXPRESSIONNODE(DefaultValue);
    virtual int64_t onGetInteger(size_t index) const { (void) index; return 0; }
    virtual double  onGetFloat(size_t index)   const { (void) index; return 0; }
    virtual ConstBufferRef onGetString(size_t index, BufferRef buf) const {
        (void) index;
        (void) buf;
        return ConstBufferRef(&null, 0);
    }
    virtual void min(const ResultNode & b) { (void) b; }
    virtual void max(const ResultNode & b) { (void) b; }
    virtual void add(const ResultNode & b) { (void) b; }
private:
    virtual void set(const ResultNode&);
    virtual size_t hash() const { return 0; }
    static char null;
};

class DocumentFieldNode : public DocumentAccessorNode
{
public:
    DECLARE_NBO_SERIALIZE;
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    DECLARE_EXPRESSIONNODE(DocumentFieldNode);
    DocumentFieldNode() : _fieldPath(), _value(), _fieldName(), _doc(NULL) { }
    DocumentFieldNode(const vespalib::stringref &name) : _fieldPath(), _value(), _fieldName(name), _doc(NULL) { }
    DocumentFieldNode(const DocumentFieldNode & rhs);
    DocumentFieldNode & operator = (const DocumentFieldNode & rhs);
    virtual const vespalib::string & getFieldName() const { return _fieldName; }
private:
    class Handler : public document::FieldValue::IteratorHandler {
    public:
        virtual void reset() = 0;
    protected:
        typedef document::FieldValue::IteratorHandler::Content Content;
    private:
        virtual void onCollectionStart(const Content & c);
        virtual void onStructStart(const Content & c);
    };
    class SingleHandler : public Handler {
    public:
        SingleHandler(ResultNode & result) : _result(result) {}
    private:
        virtual void reset() { _result.set(_defaultValue); }
        ResultNode & _result;
        static DefaultValue _defaultValue;
        virtual void onPrimitive(const Content & c);
    };
    class MultiHandler : public Handler {
    public:
        MultiHandler(ResultNodeVector & result) : _result(result) {}
    private:
        virtual void reset() { _result.clear(); }
        ResultNodeVector & _result;
        virtual void onPrimitive(const Content & c);
    };

    virtual const ResultNode & getResult() const { return *_value; }
    virtual void onPrepare(bool preserveAccurateTypes);
    virtual bool onExecute() const;
    virtual void onDoc(const document::Document & doc);
    virtual void onDocType(const document::DocumentType & docType);
    document::FieldPath    _fieldPath;
    mutable ResultNode::CP           _value;
    mutable std::unique_ptr<Handler>   _handler;
    vespalib::string                 _fieldName;
    const document::Document       * _doc;

};

}
}

