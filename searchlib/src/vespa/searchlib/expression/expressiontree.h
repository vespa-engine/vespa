// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "expressionnode.h"
#include <vespa/vespalib/objects/objectoperation.h>
#include <vespa/vespalib/objects/objectpredicate.h>
#include <vespa/searchlib/common/hitrank.h>

namespace document {
    class DocumentType;
    class Document;
}

namespace search::attribute { class IAttributeContext; }

namespace search::expression {

class AttributeNode;
class DocumentAccessorNode;
class RelevanceNode;
class InterpolatedLookup;
class ArrayAtLookup;

struct ConfigureStaticParams {
    ConfigureStaticParams (const attribute::IAttributeContext * attrCtx,
                           const document::DocumentType * docType)
        : _attrCtx(attrCtx), _docType(docType) { }
    const attribute::IAttributeContext * _attrCtx;
    const document::DocumentType * _docType;
};

class ExpressionTree : public ExpressionNode
{
public:
    DECLARE_EXPRESSIONNODE(ExpressionTree);
    class Configure : public vespalib::ObjectOperation, public vespalib::ObjectPredicate
    {
    private:
        void execute(vespalib::Identifiable &obj) override;
        bool check(const vespalib::Identifiable &obj) const override { return obj.inherits(ExpressionTree::classId); }
    };

    ExpressionTree();
    ExpressionTree(const ExpressionNode & root);
    ExpressionTree(ExpressionNode::UP root);
    ExpressionTree(const ExpressionTree & rhs);
    ExpressionTree(ExpressionTree &&) noexcept = default;
    ~ExpressionTree();
    ExpressionTree & operator = (ExpressionNode::UP rhs);
    ExpressionTree & operator = (const ExpressionTree & rhs);
    ExpressionTree & operator = (ExpressionTree &&) noexcept = default;

    bool execute(DocId docId, HitRank rank) const;
    bool execute(const document::Document & doc, HitRank rank) const;
    const ExpressionNode * getRoot() const { return _root.get(); }
    ExpressionNode * getRoot() { return _root.get(); }
    const ResultNode * getResult() const override { return _root->getResult(); }
    friend vespalib::Serializer & operator << (vespalib::Serializer & os, const ExpressionTree & et);
    friend vespalib::Deserializer & operator >> (vespalib::Deserializer & is, ExpressionTree & et);
    void swap(ExpressionTree &);
private:
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void selectMembers(const vespalib::ObjectPredicate &predicate, vespalib::ObjectOperation &operation) override;
    bool onExecute() const override { return _root->execute(); }
    void onPrepare(bool preserveAccurateTypes) override;

    typedef std::vector<AttributeNode *> AttributeNodeList;
    typedef std::vector<DocumentAccessorNode *> DocumentAccessorNodeList;
    typedef std::vector<RelevanceNode *> RelevanceNodeList;
    typedef std::vector<InterpolatedLookup *> InterpolatedLookupList;
    typedef std::vector<ArrayAtLookup *> ArrayAtLookupList;

    ExpressionNode::CP        _root;
    AttributeNodeList         _attributeNodes;
    DocumentAccessorNodeList  _documentAccessorNodes;
    RelevanceNodeList         _relevanceNodes;
    InterpolatedLookupList    _interpolatedLookupNodes;
    ArrayAtLookupList         _arrayAtLookupNodes;
};

}
