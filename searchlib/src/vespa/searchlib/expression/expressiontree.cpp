// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "expressiontree.h"
#include "documentaccessornode.h"
#include "relevancenode.h"
#include "interpolatedlookupfunctionnode.h"
#include "arrayatlookupfunctionnode.h"
#include "attributenode.h"

namespace search::expression {

using vespalib::Serializer;
using vespalib::Deserializer;

IMPLEMENT_EXPRESSIONNODE(ExpressionTree, ExpressionNode);

void
ExpressionTree::Configure::execute(vespalib::Identifiable &obj) {
    ExpressionTree &e(static_cast<ExpressionTree &>(obj));
    if (e.getRoot()) {
        e.getRoot()->prepare(false);
    }
    e.prepare(false);
}

ExpressionTree::ExpressionTree() :
    _root(),
    _attributeNodes(),
    _documentAccessorNodes(),
    _relevanceNodes(),
    _interpolatedLookupNodes(),
    _arrayAtLookupNodes()
{
    prepare(false);
}

ExpressionTree::ExpressionTree(const ExpressionNode &root) :
    _root(root.clone()),
    _attributeNodes(),
    _documentAccessorNodes(),
    _relevanceNodes(),
    _interpolatedLookupNodes(),
    _arrayAtLookupNodes()
{
    prepare(false);
}

namespace {

template<typename NODE>
class Gather : public vespalib::ObjectOperation, public vespalib::ObjectPredicate {
    std::vector<NODE *> &_list;
public:
    Gather(std::vector<NODE *> &list) : _list(list) { _list.clear(); }

    void from(ExpressionNode &root) {
        root.select(*this, *this);
    }

private:
    void execute(vespalib::Identifiable &obj) override {
        _list.push_back(&static_cast<NODE &>(obj));
    }

    bool check(const vespalib::Identifiable &obj) const override {
        return obj.inherits(NODE::classId);
    }
};

template<typename NODE>
Gather<NODE>
gather(std::vector<NODE *> &list) {
    return Gather<NODE>(list);
}

}

void
ExpressionTree::onPrepare(bool preserveAccurateTypes)
{
    (void) preserveAccurateTypes;
    if (_root.get() != NULL) {
        gather(_attributeNodes).from(*_root);
        gather(_documentAccessorNodes).from(*_root);
        gather(_relevanceNodes).from(*_root);
        gather(_interpolatedLookupNodes).from(*_root);
        gather(_arrayAtLookupNodes).from(*_root);
    }
}

ExpressionTree::ExpressionTree(ExpressionNode::UP root) :
    ExpressionNode(),
    _root(std::move(root)),
    _attributeNodes(),
    _documentAccessorNodes(),
    _relevanceNodes(),
    _interpolatedLookupNodes(),
    _arrayAtLookupNodes()
{
    prepare(false);
}

ExpressionTree::ExpressionTree(const ExpressionTree & rhs) :
    ExpressionNode(rhs),
    _root(rhs._root),
    _attributeNodes(),
    _documentAccessorNodes(),
    _relevanceNodes(),
    _interpolatedLookupNodes(),
    _arrayAtLookupNodes()
{
    prepare(false);
}

ExpressionTree &
ExpressionTree::operator = (const ExpressionTree & rhs)
{
    if (this != & rhs) {
        ExpressionTree eTree(rhs);
        swap(eTree);
    }
    return *this;
}

ExpressionTree &
ExpressionTree::operator = (ExpressionNode::UP rhs)
{
    ExpressionTree eTree(std::move(rhs));
    swap(eTree);
    return *this;
}

void
ExpressionTree::swap(ExpressionTree & e)
{
    std::swap(_root, e._root);
    _attributeNodes.swap(e._attributeNodes);
    _documentAccessorNodes.swap(e._documentAccessorNodes);
    _relevanceNodes.swap(e._relevanceNodes);
    _interpolatedLookupNodes.swap(e._interpolatedLookupNodes);
    _arrayAtLookupNodes.swap(_arrayAtLookupNodes);
}

ExpressionTree::~ExpressionTree()
{
}

bool
ExpressionTree::execute(const document::Document & doc, HitRank rank) const
{
    for(DocumentAccessorNodeList::const_iterator it(_documentAccessorNodes.begin()), mt(_documentAccessorNodes.end()); it != mt; it++) {
        (*it)->setDoc(doc);
    }
    for(RelevanceNodeList::const_iterator it(_relevanceNodes.begin()), mt(_relevanceNodes.end()); it != mt; it++) {
        (*it)->setRelevance(rank);
    }
    return _root->execute();
}

struct DocIdSetter {
    DocId _docId;
    void operator() (InterpolatedLookup *node) {
        node->setDocId(_docId);
    }
    void operator() (ArrayAtLookup *node) {
        node->setDocId(_docId);
    }
    void operator() (AttributeNode *node) {
        node->setDocId(_docId);
    }
    DocIdSetter(DocId docId) : _docId(docId) {}
};

struct RankSetter {
    HitRank _rank;
    void operator() (RelevanceNode *node) {
        node->setRelevance(_rank);
    }
    RankSetter(HitRank rank) : _rank(rank) {}
};


bool
ExpressionTree::execute(DocId docId, HitRank rank) const
{
    DocIdSetter setDocId(docId);
    RankSetter setHitRank(rank);
    std::for_each(_attributeNodes.cbegin(), _attributeNodes.cend(), setDocId);
    std::for_each(_relevanceNodes.cbegin(), _relevanceNodes.cend(), setHitRank);
    std::for_each(_interpolatedLookupNodes.cbegin(), _interpolatedLookupNodes.cend(), setDocId);
    std::for_each(_arrayAtLookupNodes.cbegin(), _arrayAtLookupNodes.cend(), setDocId);

    return _root->execute();
}

void
ExpressionTree::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "root", _root.get());
}

void
ExpressionTree::selectMembers(const vespalib::ObjectPredicate & predicate, vespalib::ObjectOperation & operation)
{
    if (_root.get()) {
        _root->select(predicate, operation);
    }
}


Serializer &
operator << (Serializer & os, const ExpressionTree & et)
{
    return os << et._root;
}

Deserializer &
operator >> (Deserializer & is, ExpressionTree & et)
{
    is >> et._root;
    et.prepare(false);
    return is;
}

}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_expression_expressiontree() {}
