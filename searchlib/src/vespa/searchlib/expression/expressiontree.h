// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/objects/objectoperation.h>
#include <vespa/vespalib/objects/objectpredicate.h>
#include <vespa/searchlib/common/hitrank.h>
#include <vespa/searchlib/expression/expressionnode.h>

namespace document {
    class DocumentType;
    class Document;
}
namespace search {

namespace attribute { class IAttributeContext; }

namespace expression {

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
    typedef vespalib::LinkedPtr<ExpressionTree> LP;
    class Configure : public vespalib::ObjectOperation, public vespalib::ObjectPredicate
    {
    private:
        virtual void execute(vespalib::Identifiable &obj);
        virtual bool check(const vespalib::Identifiable &obj) const { return obj.inherits(ExpressionTree::classId); }
    };

    ExpressionTree();
    ExpressionTree(const ExpressionNode & root);
    ExpressionTree(const ExpressionNode::CP & root);
    ExpressionTree(const ExpressionTree & rhs);
    ~ExpressionTree();
    ExpressionTree & operator = (const ExpressionTree & rhs);
    bool execute(DocId docId, HitRank rank) const;
    bool execute(const document::Document & doc, HitRank rank) const;
    const ExpressionNode * getRoot() const { return _root.get(); }
    ExpressionNode * getRoot() { return _root.get(); }
    virtual const ResultNode & getResult() const { return _root->getResult(); }
    friend vespalib::Serializer & operator << (vespalib::Serializer & os, const ExpressionTree & et);
    friend vespalib::Deserializer & operator >> (vespalib::Deserializer & is, ExpressionTree & et);
    void swap(ExpressionTree &);
private:
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    virtual void selectMembers(const vespalib::ObjectPredicate &predicate, vespalib::ObjectOperation &operation);
    virtual bool onExecute() const { return _root->execute(); }
    virtual void onPrepare(bool preserveAccurateTypes);

    typedef std::vector<AttributeNode *> AttributeNodeList;
    typedef std::vector<DocumentAccessorNode *> DocumentAccessorNodeList;
    typedef std::vector<RelevanceNode *> RelevanceNodeList;
    typedef std::vector<InterpolatedLookup *> InterpolatedLookupList;
    typedef std::vector<ArrayAtLookup *> ArrayAtLookupList;

    vespalib::IdentifiableLinkedPtr<ExpressionNode>  _root;
    AttributeNodeList        _attributeNodes;
    DocumentAccessorNodeList _documentAccessorNodes;
    RelevanceNodeList        _relevanceNodes;
    InterpolatedLookupList   _interpolatedLookupNodes;
    ArrayAtLookupList        _arrayAtLookupNodes;
};


} // namespace expression
} // namespace search

