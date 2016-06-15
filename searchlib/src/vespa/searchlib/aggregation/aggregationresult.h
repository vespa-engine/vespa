// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/expressiontree.h>
#include <vespa/searchlib/expression/numericresultnode.h>
#include <vespa/searchlib/expression/integerresultnode.h>
#include <stdexcept>

namespace search {
namespace aggregation {

using search::expression::ExpressionTree;
using search::expression::ExpressionNode;
using search::expression::ResultNode;
using search::expression::DocId;
using search::expression::NumericResultNode;
using search::expression::SingleResultNode;
using search::expression::IntegerResultNode;
using search::expression::Int64ResultNode;
using search::expression::ConfigureStaticParams;

#define DECLARE_ABSTRACT_AGGREGATIONRESULT(cclass)                  \
    DECLARE_IDENTIFIABLE_ABSTRACT_NS2(search, aggregation, cclass); \
    private:                                                        \
    public:

#define DECLARE_AGGREGATIONRESULT(cclass)                                   \
    DECLARE_IDENTIFIABLE_NS2(search, aggregation, cclass);                  \
    DECLARE_NBO_SERIALIZE;                                                  \
    virtual cclass *clone() const { return new cclass(*this); }             \
    private:                                                                \
    virtual void onMerge(const AggregationResult & b);                      \
    virtual void onAggregate(const ResultNode &result);                     \
    virtual void onReset();                                                 \
    public:

// resultNodePrimitive : countHits | hits(INTEGER) | groups(INTEGER) | xor | sum | min | max |avg


class AggregationResult : public expression::ExpressionNode
{
public:
    DECLARE_NBO_SERIALIZE;
    DECLARE_ABSTRACT_AGGREGATIONRESULT(AggregationResult);
    class Configure : public vespalib::ObjectOperation, public vespalib::ObjectPredicate
    {
    private:
        virtual void execute(vespalib::Identifiable &obj);
        virtual bool check(const vespalib::Identifiable &obj) const;
    };

    virtual void visitMembers(vespalib::ObjectVisitor & visitor) const;
    virtual void selectMembers(const vespalib::ObjectPredicate & predicate, vespalib::ObjectOperation & operation);

    void reset() { onReset(); }
    void merge(const AggregationResult & b) { onMerge(b); }
    virtual void postMerge() {}
    void aggregate(const document::Document & doc, HitRank rank) {
        bool ok(_expressionTree->execute(doc, rank));
        if (ok) {
            onAggregate(_expressionTree->getResult(), doc, rank);
        } else {
            throw std::runtime_error(vespalib::make_string("aggregate(%s, %f) failed ", doc.getId().toString().c_str(), rank));
        }
    }
    void aggregate(DocId docId, HitRank rank) {
        bool ok(_expressionTree->execute(docId, rank));
        if (ok) {
            onAggregate(_expressionTree->getResult(), docId, rank);
        } else {
            throw std::runtime_error(vespalib::make_string("aggregate(%u, %f) failed ", docId, rank));
        }
    }
    AggregationResult &setExpression(const ExpressionNode::CP &expr);
    AggregationResult &setResult(const ResultNode::CP &result) {
        prepare(result.get(), true);
        return *this;
    }

    const ResultNode & getRank() const { return onGetRank(); }
    const ResultNode & getResult() const { return onGetRank(); }
    virtual ResultNode & getResult() { return const_cast<ResultNode &>(onGetRank()); }
    virtual AggregationResult * clone() const = 0;
    const ExpressionNode * getExpression() const { return _expressionTree->getRoot().get(); }
    ExpressionNode * getExpression() { return _expressionTree->getRoot().get(); }
protected:
    AggregationResult() : _expressionTree(new ExpressionTree()), _tag(-1) { }
private:
    /// from expressionnode
    virtual void onPrepare(bool preserveAccurateTypes) { (void) preserveAccurateTypes; }
    /// from expressionnode
    virtual bool onExecute() const  { return true; }

    void prepare() { if (getExpression() != NULL) { prepare(&getExpression()->getResult(), false); } }
    void prepare(const ResultNode * result, bool useForInit) { if (result) { onPrepare(*result, useForInit); } }
    virtual void onPrepare(const ResultNode & result, bool useForInit) = 0;
    virtual void onMerge(const AggregationResult & b) = 0;
    virtual void onReset() = 0;
    virtual void onAggregate(const ResultNode &result) = 0;
    virtual const ResultNode & onGetRank() const = 0;
    virtual void onAggregate(const ResultNode &result, const document::Document & doc, HitRank rank) {
        (void) doc;
        (void) rank;
        onAggregate(result);
    }
    virtual void onAggregate(const ResultNode &result, DocId docId, HitRank rank) {
        (void) docId;
        (void) rank;
        onAggregate(result);
    }
    search::expression::ExpressionTree::LP _expressionTree;
    uint32_t _tag;
};

}
}
