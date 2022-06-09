// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/expressiontree.h>
#include <vespa/searchlib/expression/resultnode.h>

namespace search::aggregation {

using search::expression::DocId;

#define DECLARE_ABSTRACT_AGGREGATIONRESULT(cclass)                  \
    DECLARE_IDENTIFIABLE_ABSTRACT_NS2(search, aggregation, cclass); \
    private:                                                        \
    public:

#define DECLARE_AGGREGATIONRESULT_BASE(cclass)                      \
    DECLARE_IDENTIFIABLE_NS2(search, aggregation, cclass);          \
    DECLARE_NBO_SERIALIZE;

#define DECLARE_AGGREGATIONRESULT(cclass)                           \
    DECLARE_AGGREGATIONRESULT_BASE(cclass);                         \
    cclass *clone() const override { return new cclass(*this); }    \
    private:                                                        \
    void onMerge(const AggregationResult & b) override;             \
    void onAggregate(const ResultNode &result) override;            \
    void onReset() override;                                        \
    public:

// resultNodePrimitive : countHits | hits(INTEGER) | groups(INTEGER) | xor | sum | min | max |avg


class AggregationResult : public expression::ExpressionNode
{
public:
    using ResultNode = expression::ResultNode;
    DECLARE_NBO_SERIALIZE;
    DECLARE_ABSTRACT_AGGREGATIONRESULT(AggregationResult);
    AggregationResult(const AggregationResult &);
    AggregationResult & operator = (const AggregationResult &);
    AggregationResult(AggregationResult &&) = default;
    AggregationResult & operator = (AggregationResult &&) = default;
    ~AggregationResult() override;
    class Configure : public vespalib::ObjectOperation, public vespalib::ObjectPredicate
    {
    private:
        void execute(vespalib::Identifiable &obj) override;
        bool check(const vespalib::Identifiable &obj) const override;
    };

    void visitMembers(vespalib::ObjectVisitor & visitor) const override;
    void selectMembers(const vespalib::ObjectPredicate & predicate, vespalib::ObjectOperation & operation) override;

    void reset() { onReset(); }
    void merge(const AggregationResult & b) { onMerge(b); }
    virtual void postMerge() {}
    void aggregate(const document::Document & doc, HitRank rank);
    void aggregate(DocId docId, HitRank rank);
    AggregationResult &setExpression(ExpressionNode::UP expr);
    AggregationResult &setResult(const ResultNode::CP &result) {
        prepare(result.get(), true);
        return *this;
    }

    const ResultNode & getRank() const { return onGetRank(); }
    const ResultNode * getResult() const override { return &onGetRank(); }
    virtual ResultNode & getResult() { return const_cast<ResultNode &>(onGetRank()); }
    virtual AggregationResult * clone() const override = 0;
    const ExpressionNode * getExpression() const { return _expressionTree->getRoot(); }
    ExpressionNode * getExpression() { return _expressionTree->getRoot(); }
protected:
    AggregationResult();
private:
    void onPrepare(bool preserveAccurateTypes) override { (void) preserveAccurateTypes; }
    bool onExecute() const override { return true; }

    void prepare() { if (getExpression() != nullptr) { prepare(getExpression()->getResult(), false); } }
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
    std::shared_ptr<expression::ExpressionTree> _expressionTree;
    uint32_t _tag;
};

}
