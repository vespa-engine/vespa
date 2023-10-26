// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multibitvectoriterator.h"
#include "andsearch.h"
#include "andnotsearch.h"
#include "sourceblendersearch.h"
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>

namespace search::queryeval {

using vespalib::Trinary;
using vespalib::hwaccelrated::IAccelrated;
using Meta = MultiBitVectorBase::Meta;

namespace {

struct And {
    using Word = BitWord::Word;
    void operator () (const IAccelrated & accel, size_t offset, const std::vector<Meta> & src, void *dest) noexcept {
        accel.and64(offset, src, dest);
    }
    static bool isAnd() noexcept { return true; }
};

struct Or {
    using Word = BitWord::Word;
    void operator () (const IAccelrated & accel, size_t offset, const std::vector<Meta> & src, void *dest) noexcept {
        accel.or64(offset, src, dest);
    }
    static bool isAnd() noexcept { return false; }
};

}

MultiBitVectorBase::MultiBitVectorBase(size_t reserved)
    : _numDocs(std::numeric_limits<uint32_t>::max()),
      _lastMaxDocIdLimit(0),
      _lastMaxDocIdLimitRequireFetch(0),
      _lastValue(0),
      _bvs()
{
    _bvs.reserve(reserved);
}

void
MultiBitVectorBase::addBitVector(Meta bv, uint32_t docIdLimit) {
    _numDocs = std::min(_numDocs, docIdLimit);
    _bvs.push_back(bv);
}

template <typename Update>
MultiBitVector<Update>::MultiBitVector(size_t reserved)
    : MultiBitVectorBase(reserved),
      _update(),
      _accel(IAccelrated::getAccelerator()),
      _lastWords()
{
    static_assert(sizeof(_lastWords) == 64, "Lastwords should have 64 byte size");
    static_assert(NumWordsInBatch == 8, "Batch size should be 8 words.");
    memset(_lastWords, 0, sizeof(_lastWords));
}

template<typename Update>
bool
MultiBitVector<Update>::updateLastValue(uint32_t docId) noexcept
{
    if (docId >= _lastMaxDocIdLimit) {
        if (__builtin_expect(isAtEnd(docId), false)) {
            return true;
        }
        const uint32_t index(BitWord::wordNum(docId));
        if (docId >= _lastMaxDocIdLimitRequireFetch) {
            uint32_t baseIndex = index & ~(NumWordsInBatch - 1);
            _update(_accel, baseIndex*sizeof(Word), _bvs, _lastWords);
            _lastMaxDocIdLimitRequireFetch = (baseIndex + NumWordsInBatch) * BitWord::WordLen;
        }
        _lastValue = _lastWords[index % NumWordsInBatch];
        _lastMaxDocIdLimit = (index + 1) * BitWord::WordLen;
    }
    return false;
}

template<typename Update>
uint32_t
MultiBitVector<Update>::strictSeek(uint32_t docId) noexcept
{
    bool atEnd;
    for (atEnd = updateLastValue(docId), _lastValue = _lastValue & BitWord::checkTab(docId);
         (_lastValue == 0) && __builtin_expect(! atEnd, true);
         atEnd = updateLastValue(_lastMaxDocIdLimit));
    if (__builtin_expect(!atEnd, true)) {
        return _lastMaxDocIdLimit - BitWord::WordLen + vespalib::Optimized::lsbIdx(_lastValue);
    }
    return _numDocs;
}

template<typename Update>
bool
MultiBitVector<Update>::seek(uint32_t docId) noexcept
{
    bool atEnd = updateLastValue(docId);
    if (__builtin_expect( ! atEnd, true)) {
        if (_lastValue & BitWord::mask(docId)) {
            return true;
        }
    }
    return false;
}

namespace {

template<typename Update>
class MultiBitVectorIterator : public MultiBitVectorIteratorBase
{
public:
    explicit MultiBitVectorIterator(Children children)
        : MultiBitVectorIteratorBase(std::move(children)),
          _mbv(getChildren().size() + 1)
    {
        for (const auto & child : getChildren()) {
            BitVectorMeta bv = child->asBitVector();
            if (bv.valid()) {
                _mbv.addBitVector(Meta(bv.vector()->getStart(), bv.inverted()), bv.getDocidLimit());
            }
        }
    }
    void initRange(uint32_t beginId, uint32_t endId) override {
        MultiBitVectorIteratorBase::initRange(beginId, endId);
        _mbv.reset();
    }
    UP andWith(UP filter, uint32_t estimate) override;
protected:
    void doSeek(uint32_t docId) override;
    Trinary is_strict() const override { return Trinary::False; }
    bool acceptExtraFilter() const noexcept final { return _mbv.acceptExtraFilter(); }
    MultiBitVector<Update> _mbv;
};

template<typename Update>
class MultiBitVectorIteratorStrict final : public MultiBitVectorIterator<Update>
{
public:
    explicit MultiBitVectorIteratorStrict(MultiSearch::Children children)
        : MultiBitVectorIterator<Update>(std::move(children))
    { }
private:
    void doSeek(uint32_t docId) override {
        docId = this->_mbv.strictSeek(docId);
        if (__builtin_expect(this->_mbv.isAtEnd(docId), false)) {
            this->setAtEnd();
        } else {
            this->setDocId(docId);
        }
    }
    Trinary is_strict() const override { return Trinary::True; }
};

template<typename Update>
void
MultiBitVectorIterator<Update>::doSeek(uint32_t docId)
{
    if (_mbv.seek(docId)) {
        setDocId(docId);
    }
}

template <typename Update>
SearchIterator::UP
MultiBitVectorIterator<Update>::andWith(UP filter, uint32_t estimate)
{
    (void) estimate;
    BitVectorMeta bv = filter->asBitVector();
    if (bv.valid() && acceptExtraFilter()) {
        _mbv.addBitVector(Meta(bv.vector()->getStart(), bv.inverted()), bv.getDocidLimit());
        insert(getChildren().size(), std::move(filter));
        _mbv.reset();
    }
    return filter;
}

using AndBVIterator = MultiBitVectorIterator<And>;
using AndBVIteratorStrict = MultiBitVectorIteratorStrict<And>;
using OrBVIterator = MultiBitVectorIterator<Or>;
using OrBVIteratorStrict = MultiBitVectorIteratorStrict<Or>;

bool hasAtLeast2Bitvectors(const MultiSearch::Children & children)
{
    size_t count(0);
    for (const auto & search : children) {
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

MultiBitVectorIteratorBase::MultiBitVectorIteratorBase(Children children) :
    MultiSearch(std::move(children))
{ }

MultiBitVectorIteratorBase::~MultiBitVectorIteratorBase() = default;

void
MultiBitVectorIteratorBase::initRange(uint32_t beginId, uint32_t endId)
{
    MultiSearch::initRange(beginId, endId);
}

void
MultiBitVectorIteratorBase::doUnpack(uint32_t docid)
{
    if (_unpackInfo.unpackAll()) {
        MultiSearch::doUnpack(docid);
    } else {
        auto &children = getChildren();
        _unpackInfo.each([&children,docid](size_t i) { children[i]->unpack(docid); }, children.size());
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
                stolen.push_back(std::move(bit));
            } else {
                it++;
            }
        }
        SearchIterator::UP next;
        if (parent.isAnd()) {
            if (strict) {
                next = std::make_unique<AndBVIteratorStrict>(std::move(stolen));
            } else {
                next = std::make_unique<AndBVIterator>(std::move(stolen));
            }
        } else if (parent.isOr()) {
            if (strict) {
                next = std::make_unique<OrBVIteratorStrict>(std::move(stolen));
            } else {
                next = std::make_unique<OrBVIterator>(std::move(stolen));
            }
        } else if (parent.isAndNot()) {
            if (strict) {
                next = std::make_unique<OrBVIteratorStrict>(std::move(stolen));
            } else {
                next = std::make_unique<OrBVIterator>(std::move(stolen));
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
    for (auto & search : toOptimize) {
        search = optimize(std::move(search));
    }

    return parentIt;
}

}
