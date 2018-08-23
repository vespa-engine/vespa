// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "functionnode.h"
#include "resultvector.h"
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/vespalib/objects/objectoperation.h>
#include <vespa/vespalib/objects/objectpredicate.h>

namespace search::expression {

class AttributeResult : public ResultNode
{
public:
    typedef std::unique_ptr<AttributeResult> UP;
    DECLARE_RESULTNODE(AttributeResult);
    AttributeResult() : _attribute(NULL), _docId(0) { }
    AttributeResult(const attribute::IAttributeVector * attribute, DocId docId) :
        _attribute(attribute),
        _docId(docId)
    { }
    void setDocId(DocId docId) { _docId = docId; }
    const search::attribute::IAttributeVector *getAttribute() const { return _attribute; }
    DocId getDocId() const { return _docId; }
private:
    int64_t onGetInteger(size_t index) const override { (void) index; return _attribute->getInt(_docId); }
    double onGetFloat(size_t index)    const override { (void) index; return _attribute->getFloat(_docId); }
    ConstBufferRef onGetString(size_t index, BufferRef buf) const override {
        (void) index;
        const char * t = _attribute->getString(_docId, buf.str(), buf.size());
        return ConstBufferRef(t, strlen(t));
    }
    int64_t onGetEnum(size_t index) const override { (void) index; return (static_cast<int64_t>(_attribute->getEnum(_docId))); }
    void set(const search::expression::ResultNode&) override { }
    size_t hash() const override { return _docId; }

    const search::attribute::IAttributeVector * _attribute;
    DocId          _docId;
};

class AttributeNode : public FunctionNode
{
    typedef vespalib::BufferRef BufferRef;
    typedef vespalib::ConstBufferRef ConstBufferRef;
public:
    DECLARE_NBO_SERIALIZE;
    class Configure : public vespalib::ObjectOperation, public vespalib::ObjectPredicate
    {
    public:
        Configure(const search::attribute::IAttributeContext & attrCtx) : _attrCtx(attrCtx) { }
    private:
        void execute(vespalib::Identifiable &obj) override {
            static_cast<ExpressionNode &>(obj).wireAttributes(_attrCtx);
            obj.selectMembers(*this, *this);
        }
        bool check(const vespalib::Identifiable &obj) const override {
            return obj.inherits(ExpressionNode::classId);
        }
        const search::attribute::IAttributeContext & _attrCtx;
    };

    class CleanupAttributeReferences : public vespalib::ObjectOperation, public vespalib::ObjectPredicate
    {
    private:
        void execute(vespalib::Identifiable &obj) override { static_cast<AttributeNode &>(obj).cleanup(); }
        bool check(const vespalib::Identifiable &obj) const override { return obj.inherits(AttributeNode::classId); }
    };

    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    DECLARE_EXPRESSIONNODE(AttributeNode);
    AttributeNode();
    AttributeNode(vespalib::stringref name);
    AttributeNode(const search::attribute::IAttributeVector & attribute);
    AttributeNode(const AttributeNode & attribute);
    AttributeNode & operator = (const AttributeNode & attribute);
    ~AttributeNode();
    void setDocId(DocId docId) const { _scratchResult->setDocId(docId); }
    const search::attribute::IAttributeVector *getAttribute() const {
        return _scratchResult ? _scratchResult->getAttribute() : nullptr;
    }
    const vespalib::string & getAttributeName() const { return _attributeName; }

    void useEnumOptimization(bool use=true) { _useEnumOptimization = use; }
    bool hasMultiValue() const { return _hasMultiValue; }
private:
    void cleanup();
    void wireAttributes(const search::attribute::IAttributeContext & attrCtx) override;
    void onPrepare(bool preserveAccurateTypes) override;
    bool onExecute() const override;
    class Handler
    {
    public:
        virtual ~Handler() { }
        virtual void handle(const AttributeResult & r) = 0;
    };
    class IntegerHandler : public Handler
    {
    public:
        IntegerHandler(ResultNode & result) :
            Handler(),
            _vector(((IntegerResultNodeVector &)result).getVector()),
            _wVector()
        { }
        void handle(const AttributeResult & r) override;
    private:
        IntegerResultNodeVector::Vector & _vector;
        mutable std::vector<search::attribute::IAttributeVector::WeightedInt> _wVector;
    };
    class FloatHandler : public Handler
    {
    public:
        FloatHandler(ResultNode & result) :
            Handler(),
            _vector(((FloatResultNodeVector &)result).getVector()),
            _wVector()
        { }
        void handle(const AttributeResult & r) override;
    private:
        FloatResultNodeVector::Vector & _vector;
        mutable std::vector<search::attribute::IAttributeVector::WeightedFloat> _wVector;
    };
    class StringHandler : public Handler
    {
    public:
        StringHandler(ResultNode & result) :
            Handler(),
            _vector(((StringResultNodeVector &)result).getVector()),
            _wVector()
        { }
        void handle(const AttributeResult & r) override;
    private:
        StringResultNodeVector::Vector & _vector;
        mutable std::vector<search::attribute::IAttributeVector::WeightedConstChar> _wVector;
    };
    class EnumHandler : public Handler
    {
    public:
        EnumHandler(ResultNode & result) :
            Handler(),
            _vector(((EnumResultNodeVector &)result).getVector()),
            _wVector()
        { }
        void handle(const AttributeResult & r) override;
    private:
        EnumResultNodeVector::Vector &_vector;
        mutable std::vector<search::attribute::IAttributeVector::WeightedEnum> _wVector;
    };

    AttributeResult::UP         _scratchResult;
    bool                        _hasMultiValue;
    bool                        _useEnumOptimization;
    std::unique_ptr<Handler>    _handler;
    vespalib::string            _attributeName;
};

}
