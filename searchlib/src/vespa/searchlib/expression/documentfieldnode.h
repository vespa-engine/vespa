// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentaccessornode.h"
#include "resultnode.h"
#include "resultvector.h"
#include <vespa/document/fieldvalue/iteratorhandler.h>
#include <vespa/document/base/fieldpath.h>

namespace search::expression {

class DefaultValue final : public ResultNode
{
public:
    DECLARE_EXPRESSIONNODE(DefaultValue);
    int64_t onGetInteger(size_t index) const override { (void) index; return 0; }
    double  onGetFloat(size_t index)   const override { (void) index; return 0; }
    ConstBufferRef onGetString(size_t index, BufferRef buf) const override {
        (void) index;
        (void) buf;
        return ConstBufferRef(&null, 0);
    }
private:
    void set(const ResultNode&) override;
    size_t hash() const override { return 0; }
    static char null;
};

class DocumentFieldNode : public DocumentAccessorNode
{
public:
    DECLARE_NBO_SERIALIZE;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    DECLARE_EXPRESSIONNODE(DocumentFieldNode);
    DocumentFieldNode() : _fieldPath(), _value(), _fieldName(), _doc(nullptr) { }
    ~DocumentFieldNode() override;
    DocumentFieldNode(vespalib::stringref name) : _fieldPath(), _value(), _fieldName(name), _doc(nullptr) { }
    DocumentFieldNode(const DocumentFieldNode & rhs);
    DocumentFieldNode & operator = (const DocumentFieldNode & rhs);
    DocumentFieldNode(DocumentFieldNode && rhs) noexcept = default;
    DocumentFieldNode & operator = (DocumentFieldNode && rhs) noexcept = default;
    const vespalib::string & getFieldName() const override { return _fieldName; }
private:
    class Handler : public document::fieldvalue::IteratorHandler {
    public:
        virtual void reset() = 0;
    private:
        void onCollectionStart(const Content & c) override;
        void onStructStart(const Content & c) override;
    };
    class SingleHandler : public Handler {
    public:
        SingleHandler(ResultNode & result) : _result(result) {}
    private:
        void reset() override { _result.set(_defaultValue); }
        ResultNode & _result;
        static DefaultValue _defaultValue;
        void onPrimitive(uint32_t fid, const Content & c) override;
    };
    class MultiHandler : public Handler {
    public:
        MultiHandler(ResultNodeVector & result) : _result(result) {}
    private:
        void reset() override { _result.clear(); }
        ResultNodeVector & _result;
        void onPrimitive(uint32_t fid, const Content & c) override;
    };

    const ResultNode * getResult() const override { return _value.get(); }
    void onPrepare(bool preserveAccurateTypes) override;
    bool onExecute() const override;
    void onDoc(const document::Document & doc) override;
    void onDocType(const document::DocumentType & docType) override;
    document::FieldPath                _fieldPath;
    mutable ResultNode::CP             _value;
    mutable std::unique_ptr<Handler>   _handler;
    vespalib::string                   _fieldName;
    const document::Document         * _doc;

};

}
