// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stackdumpcreator.h"

#include "intermediatenodes.h"
#include "termnodes.h"
#include <vespa/vespalib/objects/nbo.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/searchlib/parsequery/parse.h>

using vespalib::string;
using std::vector;
using search::ParseItem;
using search::RawBuf;
using namespace search::query;

namespace {
class QueryNodeConverter : public QueryVisitor {
    RawBuf _buf;

    void visitNodes(const vector<Node *> &nodes) {
        for (size_t i = 0; i < nodes.size(); ++i) {
            nodes[i]->accept(*this);
        }
    }

    void appendString(const string &s) {
        _buf.preAlloc(sizeof(uint32_t) + s.size());
        _buf.appendCompressedPositiveNumber(s.size());
        _buf.append(s.data(), s.size());
    }

    void appendCompressedPositiveNumber(uint64_t n) {
        _buf.appendCompressedPositiveNumber(n);
    }

    void appendCompressedNumber(int64_t n) {
        _buf.appendCompressedNumber(n);
    }

    void appendInt(uint32_t i) {
        _buf.preAlloc(sizeof(uint32_t));
        _buf.PutToInet(i);
    }

    void appendLong(uint64_t l) {
        _buf.preAlloc(sizeof(uint64_t));
        _buf.Put64ToInet(l);
    }

    void appendByte(uint8_t i) {
        _buf.preAlloc(sizeof(uint8_t));
        _buf.append(&i, sizeof(uint8_t));
    }

    void appendDouble(double i) {
        _buf.preAlloc(sizeof(double));
        double nboVal = vespalib::nbo::n2h(i);
        _buf.append(&nboVal, sizeof(double));
    }
    void append(const vespalib::string &s) { appendString(s); }
    void append(uint64_t l) { appendLong(l); }

    template <typename V>
    void appendPredicateQueryTermVector(const V& v);

    void createComplexIntermediate(const Term &node, const std::vector<Node *> & children, size_t type) {
        uint8_t flags = 0;
        if (!node.isRanked()) {
            flags |= ParseItem::IFLAG_NORANK;
        }
        if (!node.usePositionData()) {
            flags |= ParseItem::IFLAG_NOPOSITIONDATA;
        }
        if (flags != 0) {
            type |= ParseItem::IF_FLAGS;
        }
        appendByte(type);
        appendCompressedNumber(node.getWeight().percent());
        if (type & ParseItem::IF_FLAGS) {
            appendByte(flags);
        }
        appendCompressedPositiveNumber(children.size());
        appendString(node.getView());
        visitNodes(children);
    }

    void createIntermediate(const Intermediate &node, size_t type) {
        appendByte(type);
        appendCompressedPositiveNumber(node.getChildren().size());
        visitNodes(node.getChildren());
    }

    void createIntermediate(const Intermediate &node, size_t type, size_t distance) {
        appendByte(type);
        appendCompressedPositiveNumber(node.getChildren().size());
        appendCompressedPositiveNumber(distance);
        visitNodes(node.getChildren());
    }

    void createIntermediate(const Intermediate &node, size_t type, const vespalib::string & view) {
        appendByte(type);
        appendCompressedPositiveNumber(node.getChildren().size());
        appendString(view);
        visitNodes(node.getChildren());
    }

    void createIntermediate(const Intermediate &node, size_t type, size_t distance, const vespalib::string & view) {
        appendByte(type);
        appendCompressedPositiveNumber(node.getChildren().size());
        appendCompressedPositiveNumber(distance);
        appendString(view);
        visitNodes(node.getChildren());
    }

    void visit(And &node) override {
        createIntermediate(node, ParseItem::ITEM_AND);
    }

    void visit(AndNot &node) override {
        createIntermediate(node, ParseItem::ITEM_NOT);
    }

    void visit(Near &node) override {
        createIntermediate(node, ParseItem::ITEM_NEAR, node.getDistance());
    }

    void visit(ONear &node) override {
        createIntermediate(node, ParseItem::ITEM_ONEAR, node.getDistance());
    }

    void visit(Or &node) override {
        createIntermediate(node, ParseItem::ITEM_OR);
    }

    void visit(WeakAnd &node) override {
        createIntermediate(node, ParseItem::ITEM_WEAK_AND, node.getMinHits(), node.getView());
    }

    void visit(Equiv &node) override {
        createIntermediate(node, ParseItem::ITEM_EQUIV);
    }

    void visit(SameElement &node) override {
        createIntermediate(node, ParseItem::ITEM_SAME_ELEMENT, node.getView());
    }

    void visit(Phrase &node) override {
        createComplexIntermediate(node, node.getChildren(), (ParseItem::ITEM_PHRASE | ParseItem::IF_WEIGHT));
    }

    template <typename NODE>
    void createWeightedSet(NODE &node, uint8_t typefield) {
        uint8_t flags = 0;
        if (!node.isRanked()) {
            flags |= ParseItem::IFLAG_NORANK;
        }
        // usePositionData should not have any effect
        // but is propagated anyway
        if (!node.usePositionData()) {
            flags |= ParseItem::IFLAG_NOPOSITIONDATA;
        }
        if (flags != 0) {
            typefield |= ParseItem::IF_FLAGS;
        }
        appendByte(typefield);
        appendCompressedNumber(node.getWeight().percent());
        if (typefield & ParseItem::IF_FLAGS) {
            appendByte(flags);
        }
        appendCompressedPositiveNumber(node.getChildren().size());
        appendString(node.getView());
    }

    void visit(WeightedSetTerm &node) override {
        createWeightedSet(node, ParseItem::ITEM_WEIGHTED_SET | ParseItem::IF_WEIGHT);
        visitNodes(node.getChildren());
    }

    void visit(DotProduct &node) override {
        createWeightedSet(node, ParseItem::ITEM_DOT_PRODUCT | ParseItem::IF_WEIGHT);
        visitNodes(node.getChildren());
    }

    void visit(WandTerm &node) override {
        createWeightedSet(node, ParseItem::ITEM_WAND | ParseItem::IF_WEIGHT);
        appendCompressedPositiveNumber(node.getTargetNumHits());
        appendDouble(node.getScoreThreshold());
        appendDouble(node.getThresholdBoostFactor());
        visitNodes(node.getChildren());
    }

    void visit(Rank &node) override {
        createIntermediate(node, ParseItem::ITEM_RANK);
    }

    template <typename T> void appendTerm(const TermBase<T> &node);

    void createTermNode(const TermNode &node, size_t type) {
        uint8_t typefield = type | ParseItem::IF_WEIGHT | ParseItem::IF_UNIQUEID;
        uint8_t flags = 0;
        if (!node.isRanked()) {
            flags |= ParseItem::IFLAG_NORANK;
        }
        if (!node.usePositionData()) {
            flags |= ParseItem::IFLAG_NOPOSITIONDATA;
        }
        if (flags != 0) {
            typefield |= ParseItem::IF_FLAGS;
        }
        appendByte(typefield);
        appendCompressedNumber(node.getWeight().percent());
        appendCompressedPositiveNumber(node.getId());
        if (typefield & ParseItem::IF_FLAGS) {
            appendByte(flags);
        }
        appendString(node.getView());
    }

    template <class Term>
    void createTerm(const Term &node, size_t type) {
        createTermNode(node, type);
        appendTerm(node);
    }

    void visit(NumberTerm &node) override {
        createTerm(node, ParseItem::ITEM_NUMTERM);
    }

    void visit(LocationTerm &node) override {
        createTerm(node, ParseItem::ITEM_LOCATION_TERM);
    }

    void visit(PrefixTerm &node) override {
        createTerm(node, ParseItem::ITEM_PREFIXTERM);
    }

    void visit(RangeTerm &node) override {
        createTerm(node, ParseItem::ITEM_NUMTERM);
    }

    void visit(StringTerm &node) override {
        createTerm(node, ParseItem::ITEM_TERM);
    }

    void visit(SubstringTerm &node) override {
        createTerm(node, ParseItem::ITEM_SUBSTRINGTERM);
    }

    void visit(SuffixTerm &node) override {
        createTerm(node, ParseItem::ITEM_SUFFIXTERM);
    }

    void visit(PredicateQuery &node) override {
        createTerm(node, ParseItem::ITEM_PREDICATE_QUERY);
    }

    void visit(RegExpTerm &node) override {
        createTerm(node, ParseItem::ITEM_REGEXP);
    }

    void visit(NearestNeighborTerm &node) override {
        createTermNode(node, ParseItem::ITEM_NEAREST_NEIGHBOR);
        appendString(node.get_query_tensor_name());
        appendCompressedPositiveNumber(node.get_target_num_hits());
        appendCompressedPositiveNumber(node.get_allow_approximate() ? 1 : 0);
        appendCompressedPositiveNumber(node.get_explore_additional_hits());
    }

public:
    QueryNodeConverter()
        : _buf(4096)
    {
    }

    string getStackDump() {
        return string(_buf.GetDrainPos(),
                      _buf.GetDrainPos() + _buf.GetUsedLen());
    }
};

template <typename T>
void QueryNodeConverter::appendTerm(const TermBase<T> &node) {
    vespalib::asciistream ost;
    ost << node.getTerm();
    appendString(ost.str());
}
template <>
void QueryNodeConverter::appendTerm(const TermBase<string> &node) {
    appendString(node.getTerm());
}
template <>
void QueryNodeConverter::appendTerm(
        const TermBase<PredicateQueryTerm::UP> &node) {
    const PredicateQueryTerm &term = *node.getTerm();
    appendPredicateQueryTermVector(term.getFeatures());
    appendPredicateQueryTermVector(term.getRangeFeatures());
}
template <typename V>
void QueryNodeConverter::appendPredicateQueryTermVector(const V& v) {
    appendCompressedNumber(v.size());
    for (const auto &entry : v) {
        append(entry.getKey());
        append(entry.getValue());
        append(entry.getSubQueryBitmap());
    }
}
}  // namespace

string StackDumpCreator::create(const Node &node) {
    QueryNodeConverter converter;
    const_cast<Node &>(node).accept(converter);
    return converter.getStackDump();
}

using namespace search::query;
