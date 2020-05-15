// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/queryeval/multibitvectoriterator.h>
#include <vespa/searchlib/queryeval/andsearch.h>
#include <vespa/searchlib/queryeval/andnotsearch.h>
#include <vespa/searchlib/queryeval/sourceblendersearch.h>
#include <vespa/searchlib/queryeval/orsearch.h>
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/attribute/attributeiterators.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/vespalib/util/optimized.h>

namespace search::queryeval {

using vespalib::Trinary;

namespace {

template<typename Update>
class MultiBitVectorIterator : public MultiBitVectorIteratorBase
{
public:
    MultiBitVectorIterator(const Children & children) : MultiBitVectorIteratorBase(children) { }
protected:
    void updateLastValue(uint32_t docId);
    void strictSeek(uint32_t docId);
private:
    void doSeek(uint32_t docId) override;
    Trinary is_strict() const override { return Trinary::False; }
    bool acceptExtraFilter() const override { return Update::isAnd(); }
    Update                  _update;
};

template<typename Update>
class MultiBitVectorIteratorStrict : public MultiBitVectorIterator<Update>
{
public:
    MultiBitVectorIteratorStrict(const MultiSearch::Children & children) : MultiBitVectorIterator<Update>(children) { }
private:
    void doSeek(uint32_t docId) override { this->strictSeek(docId); }
    Trinary is_strict() const override { return Trinary::True; }
};

template<typename Update>
void MultiBitVectorIterator<Update>::updateLastValue(uint32_t docId)
{
    if (docId >= _lastMaxDocIdLimit) {
        if (__builtin_expect(docId < _numDocs, true)) {
            const uint32_t index(wordNum(docId));
            _lastValue = _bvs[0][index];
            for(uint32_t i(1); i < _bvs.size(); i++) {
                _lastValue = _update(_lastValue, _bvs[i][index]);
            }
            _lastMaxDocIdLimit = (index + 1) * WordLen;
        } else {
            setAtEnd();
        }
    }
}

template<typename Update>
void
MultiBitVectorIterator<Update>::doSeek(uint32_t docId)
{
    updateLastValue(docId);
    if (__builtin_expect( ! isAtEnd(), true)) {
        if (_lastValue & mask(docId)) {
            setDocId(docId);
        }
    }
}

template<typename Update>
void
MultiBitVectorIterator<Update>::strictSeek(uint32_t docId)
{
    for (updateLastValue(docId), _lastValue=_lastValue & checkTab(docId);
         (_lastValue == 0) && __builtin_expect(! isAtEnd(), true);
         updateLastValue(_lastMaxDocIdLimit));
    if (__builtin_expect(!isAtEnd(), true)) {
        docId = _lastMaxDocIdLimit - WordLen + vespalib::Optimized::lsbIdx(_lastValue);
        if (__builtin_expect(docId >= _numDocs, false)) {
            setAtEnd();
        } else {
            setDocId(docId);
        }
    }
}

struct And {
    typedef BitWord::Word Word;
    Word operator () (const Word a, const Word b) {
        return a & b;
    }
    static bool isAnd() { return true; }
};

struct Or {
    typedef BitWord::Word Word;
    Word operator () (const Word a, const Word b) {
        return a | b;
    }
    static bool isAnd() { return false; }
};

typedef MultiBitVectorIterator<And> AndBVIterator;
typedef MultiBitVectorIteratorStrict<And> AndBVIteratorStrict;
typedef MultiBitVectorIterator<Or> OrBVIterator;
typedef MultiBitVectorIteratorStrict<Or> OrBVIteratorStrict;

bool hasAtLeast2Bitvectors(const MultiSearch::Children & children)
{
    size_t count(0);
    for (const SearchIterator * search : children) {
        if (search->isBitVector()) {
            count++;
        }
    }
    return count >= 2;
}

size_t firstStealable(const MultiSearch & s)
{
    return s.isAndNot() ? 1 : 0;
}

bool canOptimize(const MultiSearch & s) {
    return (s.getChildren().size() >= 2)
           && (s.isAnd() || s.isOr() || s.isAndNot())
           && hasAtLeast2Bitvectors(s.getChildren());
}

}

MultiBitVectorIteratorBase::MultiBitVectorIteratorBase(const Children & children) :
    MultiSearch(children),
    _numDocs(std::numeric_limits<unsigned int>::max()),
    _lastValue(0),
    _lastMaxDocIdLimit(0),
    _bvs()
{
    _bvs.reserve(children.size());
    for (size_t i(0); i < children.size(); i++) {
        const auto * bv = static_cast<const BitVectorIterator *>(children[i]);
        _bvs.emplace_back(reinterpret_cast<const Word *>(bv->getBitValues()), bv->isInverted());
        _numDocs = std::min(_numDocs, bv->getDocIdLimit());
    }
}

MultiBitVectorIteratorBase::~MultiBitVectorIteratorBase() = default;

void
MultiBitVectorIteratorBase::initRange(uint32_t beginId, uint32_t endId)
{
    MultiSearch::initRange(beginId, endId);
    _lastMaxDocIdLimit = 0;
}

SearchIterator::UP
MultiBitVectorIteratorBase::andWith(UP filter, uint32_t estimate)
{
    (void) estimate;
    if (filter->isBitVector() && acceptExtraFilter()) {
        const auto & bv = static_cast<const BitVectorIterator &>(*filter);
        _bvs.emplace_back(reinterpret_cast<const Word *>(bv.getBitValues()), bv.isInverted());
        insert(getChildren().size(), std::move(filter));
        _lastMaxDocIdLimit = 0;  // force reload
    }
    return filter;
}

void
MultiBitVectorIteratorBase::doUnpack(uint32_t docid)
{
    if (_unpackInfo.unpackAll()) {
        MultiSearch::doUnpack(docid);
    } else {
        auto &children = getChildren();
        _unpackInfo.each([&children,docid](size_t i) {
                             static_cast<BitVectorIterator *>(children[i])->unpack(docid);
                         }, children.size());
    }
}

SearchIterator::UP
MultiBitVectorIteratorBase::optimize(SearchIterator::UP parentIt)
{
    if (parentIt->isSourceBlender()) {
        auto & parent(static_cast<SourceBlenderSearch &>(*parentIt));
        for (size_t i(0); i < parent.getNumChildren(); i++) {
            parent.setChild(i, optimize(parent.steal(i)));
        }
    } else if (parentIt->isMultiSearch()) {
        parentIt = optimizeMultiSearch(std::move(parentIt));
    }
    return parentIt;
}

SearchIterator::UP
MultiBitVectorIteratorBase::optimizeMultiSearch(SearchIterator::UP parentIt)
{
    auto & parent(static_cast<MultiSearch &>(*parentIt));
    if (canOptimize(parent)) {
        MultiSearch::Children stolen;
        std::vector<size_t> _unpackIndex;
        bool strict(false);
        size_t insertPosition(0);
        for (size_t it(firstStealable(parent)); it != parent.getChildren().size(); ) {
            if (parent.getChildren()[it]->isBitVector()) {
                if (stolen.empty()) {
                    insertPosition = it;
                }
                if (parent.needUnpack(it)) {
                    _unpackIndex.push_back(stolen.size());
                }
                SearchIterator::UP bit = parent.remove(it);
                if ( ! strict && (bit->is_strict() == Trinary::True)) {
                    strict = true;
                }
                stolen.push_back(bit.release());
            } else {
                it++;
            }
        }
        SearchIterator::UP next;
        if (parent.isAnd()) {
            if (strict) {
                next = std::make_unique<AndBVIteratorStrict>(stolen);
            } else {
                next = std::make_unique<AndBVIterator>(stolen);
            }
        } else if (parent.isOr()) {
            if (strict) {
                next = std::make_unique<OrBVIteratorStrict>(stolen);
            } else {
                next = std::make_unique<OrBVIterator>(stolen);
            }
        } else if (parent.isAndNot()) {
            if (strict) {
                next = std::make_unique<OrBVIteratorStrict>(stolen);
            } else {
                next = std::make_unique<OrBVIterator>(stolen);
            }
        }
        auto & nextM(static_cast<MultiBitVectorIteratorBase &>(*next));
        for (size_t index : _unpackIndex) {
            nextM.addUnpackIndex(index);
        }
        if (parent.getChildren().empty()) {
            return next;
        } else {
            parent.insert(insertPosition, std::move(next));
        }
    }
    auto & toOptimize(const_cast<MultiSearch::Children &>(parent.getChildren()));
    for (SearchIterator * & search : toOptimize) {
        search = optimize(MultiSearch::UP(search)).release();
    }

    return parentIt;
}

}
