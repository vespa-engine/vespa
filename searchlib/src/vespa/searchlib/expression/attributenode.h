// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "functionnode.h"
#include "resultvector.h"
#include "attributeresult.h"
#include <vespa/vespalib/objects/objectoperation.h>
#include <vespa/vespalib/objects/objectpredicate.h>

namespace search::attribute { class IAttributeContext; }

namespace search::expression {

class AttributeNode : public FunctionNode
{
    using BufferRef = vespalib::BufferRef;
    using ConstBufferRef = vespalib::ConstBufferRef;
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
    ~AttributeNode() override;
    void setDocId(DocId docId) const { _scratchResult->setDocId(docId); }
    const search::attribute::IAttributeVector *getAttribute() const {
        return _scratchResult ? _scratchResult->getAttribute() : nullptr;
    }
    const vespalib::string & getAttributeName() const { return _attributeName; }

    void useEnumOptimization(bool use=true) { _useEnumOptimization = use; }
    bool hasMultiValue() const { return _hasMultiValue; }
public:
    class Handler
    {
    public:
        virtual ~Handler() = default;
        virtual void handle(const AttributeResult & r) = 0;
    };
private:
    template <typename V> class IntegerHandler;
    class FloatHandler;
    class StringHandler;
    class EnumHandler;
protected:
    virtual void cleanup();
    void wireAttributes(const search::attribute::IAttributeContext & attrCtx) override;
    void onPrepare(bool preserveAccurateTypes) override;
    bool onExecute() const override;

    std::unique_ptr<AttributeResult> _scratchResult;
    bool                             _hasMultiValue;
    bool                             _useEnumOptimization;
    std::unique_ptr<Handler>         _handler;
    vespalib::string                 _attributeName;
};

}
