// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stackdumpcreator.h"

#include "intermediatenodes.h"
#include "queryvisitor.h"
#include "termnodes.h"
#include <vespa/vespalib/objects/nbo.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/searchlib/parsequery/parse.h>
#include <vespa/searchlib/util/rawbuf.h>

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

    void createIntermediate(const Intermediate &node, size_t type) {
        appendByte(type);
        appendCompressedPositiveNumber(node.getChildren().size());
        visitNodes(node.getChildren());
    }

    void createIntermediate(const Intermediate &node, size_t type,
                            size_t distance) {
        appendByte(type);
        appendCompressedPositiveNumber(node.getChildren().size());
        appendCompressedPositiveNumber(distance);
        visitNodes(node.getChildren());
    }

    void createIntermediate(const Intermediate &node, size_t type,
                            size_t distance,
                            const vespalib::string & view) {
        appendByte(type);
        appendCompressedPositiveNumber(node.getChildren().size());
        appendCompressedPositiveNumber(distance);
        appendString(view);
        visitNodes(node.getChildren());
    }

    virtual void visit(And &node) {
        createIntermediate(node, ParseItem::ITEM_AND);
    }

    virtual void visit(AndNot &node) {
        createIntermediate(node, ParseItem::ITEM_NOT);
    }

    virtual void visit(Near &node) {
        createIntermediate(node, ParseItem::ITEM_NEAR, node.getDistance());
    }

    virtual void visit(ONear &node) {
        createIntermediate(node, ParseItem::ITEM_ONEAR, node.getDistance());
    }

    virtual void visit(Or &node) {
        createIntermediate(node, ParseItem::ITEM_OR);
    }

    virtual void visit(WeakAnd &node) {
        createIntermediate(node, ParseItem::ITEM_WEAK_AND, node.getMinHits(), node.getView());
    }

    virtual void visit(Equiv &node) {
        createIntermediate(node, ParseItem::ITEM_EQUIV);
    }

    virtual void visit(Phrase &node) {
        uint8_t typefield = (ParseItem::ITEM_PHRASE | ParseItem::IF_WEIGHT);
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
        if (typefield & ParseItem::IF_FLAGS) {
            appendByte(flags);
        }
        appendCompressedPositiveNumber(node.getChildren().size());
        appendString(node.getView());
        visitNodes(node.getChildren());
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

    virtual void visit(WeightedSetTerm &node) {
        createWeightedSet(node, ParseItem::ITEM_WEIGHTED_SET | ParseItem::IF_WEIGHT);
        visitNodes(node.getChildren());
    }

    virtual void visit(DotProduct &node) {
        createWeightedSet(node, ParseItem::ITEM_DOT_PRODUCT | ParseItem::IF_WEIGHT);
        visitNodes(node.getChildren());
    }

    virtual void visit(WandTerm &node) {
        createWeightedSet(node, ParseItem::ITEM_WAND | ParseItem::IF_WEIGHT);
        appendCompressedPositiveNumber(node.getTargetNumHits());
        appendDouble(node.getScoreThreshold());
        appendDouble(node.getThresholdBoostFactor());
        visitNodes(node.getChildren());
    }

    virtual void visit(Rank &node) {
        createIntermediate(node, ParseItem::ITEM_RANK);
    }

    template <typename T> void appendTerm(const TermBase<T> &node);

    template <class Term>
    void createTerm(const Term &node, size_t type) {
        uint8_t typefield = type |
                             ParseItem::IF_WEIGHT |
                             ParseItem::IF_UNIQUEID;
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
        appendTerm(node);
    }

    virtual void visit(NumberTerm &node) {
        createTerm(node, ParseItem::ITEM_NUMTERM);
    }

    virtual void visit(LocationTerm &node) {
        createTerm(node, ParseItem::ITEM_NUMTERM);
    }

    virtual void visit(PrefixTerm &node) {
        createTerm(node, ParseItem::ITEM_PREFIXTERM);
    }

    virtual void visit(RangeTerm &node) {
        createTerm(node, ParseItem::ITEM_NUMTERM);
    }

    virtual void visit(StringTerm &node) {
        createTerm(node, ParseItem::ITEM_TERM);
    }

    virtual void visit(SubstringTerm &node) {
        createTerm(node, ParseItem::ITEM_SUBSTRINGTERM);
    }

    virtual void visit(SuffixTerm &node) {
        createTerm(node, ParseItem::ITEM_SUFFIXTERM);
    }

    virtual void visit(PredicateQuery &node) {
        createTerm(node, ParseItem::ITEM_PREDICATE_QUERY);
    }

    virtual void visit(RegExpTerm &node) {
        createTerm(node, ParseItem::ITEM_REGEXP);
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
