// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "currentindex.h"
#include "functionnode.h"
#include "attributeresult.h"
#include "current_index_setup.h"
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
        Configure(const attribute::IAttributeContext & attrCtx) : _attrCtx(attrCtx) { }
    private:
        void execute(vespalib::Identifiable &obj) override {
            static_cast<ExpressionNode &>(obj).wireAttributes(_attrCtx);
            obj.selectMembers(*this, *this);
        }
        bool check(const vespalib::Identifiable &obj) const override {
            return obj.inherits(ExpressionNode::classId);
        }
        const attribute::IAttributeContext & _attrCtx;
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
    AttributeNode(std::string_view name);
    AttributeNode(const attribute::IAttributeVector & attribute);
    AttributeNode(const AttributeNode & attribute);
    AttributeNode & operator = (const AttributeNode & attribute);
    ~AttributeNode() override;
    void setDocId(DocId docId);
    const CurrentIndex *getCurrentIndex() { return _currentIndex; }
    void setCurrentIndex(const CurrentIndex * index) { _currentIndex = index; }
    const attribute::IAttributeVector *getAttribute() const {
        return _scratchResult ? _scratchResult->getAttribute() : nullptr;
    }
    const std::string & getAttributeName() const noexcept { return _attributeName; }
    [[nodiscard]] bool hasMultiValue() const noexcept { return _hasMultiValue; }
    void enableEnumOptimization(bool enable) noexcept { _useEnumOptimization = enable; }

    class Handler
    {
    public:
        virtual ~Handler() = default;
        virtual void handle(const AttributeResult & r) = 0;
    };
private:
    virtual std::pair<std::unique_ptr<ResultNode>, std::unique_ptr<Handler>>
    createResultHandler(bool preserveAccurateType, const attribute::IAttributeVector & attribute) const;
    template <typename V, typename T> class IntegerHandler;
    template <typename T> class FloatHandler;
    class StringHandler;
    class EnumHandler;
    void wireAttributes(const attribute::IAttributeContext & attrCtx) override;
    void onPrepare(bool preserveAccurateTypes) final;

    std::unique_ptr<AttributeResult>  _scratchResult;
    const CurrentIndex               *_currentIndex;
    std::unique_ptr<ResultNodeVector> _keepAliveForIndexLookups;
    bool                              _hasMultiValue;
    bool                              _useEnumOptimization;
    mutable bool                      _needExecute;
    std::unique_ptr<Handler>          _handler;
protected:
    void setHasMultiValue(bool has) noexcept { _hasMultiValue = has; }
    [[nodiscard]] bool useEnumOptimization() const noexcept { return _useEnumOptimization; }

    void setScratchResult(std::unique_ptr<AttributeResult> result) noexcept {
        _scratchResult = std::move(result);
    }
    virtual void cleanup();
    bool onExecute() const override;
    std::string                  _attributeName;
};

}
