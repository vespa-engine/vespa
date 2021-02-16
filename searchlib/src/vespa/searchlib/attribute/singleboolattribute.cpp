// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "singleboolattribute.h"
#include "attributevector.hpp"
#include "primitivereader.h"
#include "iattributesavetarget.h"
#include "ipostinglistsearchcontext.h"
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/util/size_literals.h>

namespace search {

using attribute::Config;

SingleBoolAttribute::
SingleBoolAttribute(const vespalib::string &baseFileName, const GrowStrategy & grow)
    : IntegerAttributeTemplate<int8_t>(baseFileName, Config(BasicType::BOOL, CollectionType::SINGLE).setGrowStrategy(grow), BasicType::BOOL),
      _bv(0, 0, getGenerationHolder())
{
}

SingleBoolAttribute::~SingleBoolAttribute()
{
    getGenerationHolder().clearHoldLists();
}

void
SingleBoolAttribute::ensureRoom(DocId docIdLimit) {
    if (_bv.capacity() < docIdLimit) {
        const GrowStrategy & gs = this->getConfig().getGrowStrategy();
        uint32_t newSize = docIdLimit + (docIdLimit * gs.getDocsGrowFactor()) + gs.getDocsGrowDelta();
        bool incGen = _bv.reserve(newSize);
        if (incGen) {
            incGeneration();
        }
    }
}

bool
SingleBoolAttribute::addDoc(DocId & doc) {
    DocId docIdLimit = getNumDocs()+1;
    ensureRoom(docIdLimit);
    bool incGen = _bv.extend(docIdLimit);
    assert( ! incGen);
    incNumDocs();
    doc = getNumDocs() - 1;
    updateUncommittedDocIdLimit(doc);
    removeAllOldGenerations();
    return true;
}

void
SingleBoolAttribute::onCommit() {
    checkSetMaxValueCount(1);

    if ( ! _changes.empty()) {
        // apply updates
        ValueModifier valueGuard(getValueModifier());
        for (const auto & change : _changes) {
            if (change._type == ChangeBase::UPDATE) {
                std::atomic_thread_fence(std::memory_order_release);
                setBit(change._doc, change._data != 0);
            } else if ((change._type >= ChangeBase::ADD) && (change._type <= ChangeBase::DIV)) {
                std::atomic_thread_fence(std::memory_order_release);
                int8_t val = applyArithmetic(getFast(change._doc), change);
                setBit(change._doc, val != 0);
            } else if (change._type == ChangeBase::CLEARDOC) {
                std::atomic_thread_fence(std::memory_order_release);
                _bv.clearBitAndMaintainCount(change._doc);
            }
        }
    }

    std::atomic_thread_fence(std::memory_order_release);
    removeAllOldGenerations();

    _changes.clear();
}

void
SingleBoolAttribute::onAddDocs(DocId docIdLimit) {
    ensureRoom(docIdLimit);
}

void
SingleBoolAttribute::onUpdateStat() {
    vespalib::MemoryUsage usage;
    usage.setAllocatedBytes(_bv.extraByteSize());
    usage.setUsedBytes(_bv.sizeBytes());
    usage.mergeGenerationHeldBytes(getGenerationHolder().getHeldBytes());
    usage.merge(this->getChangeVectorMemoryUsage());
    this->updateStatistics(_bv.size(), _bv.size(), usage.allocatedBytes(), usage.usedBytes(),
                           usage.deadBytes(), usage.allocatedBytesOnHold());
}

namespace {

class BitVectorSearchContext : public AttributeVector::SearchContext, public attribute::IPostingListSearchContext
{
private:
    const BitVector & _bv;
    bool _invert;
    bool _valid;
    bool valid() const override { return _valid; }
    int32_t onFind(DocId docId, int32_t elemId, int32_t & weight) const override final {
        if ((elemId == 0) && (_invert != _bv.testBit(docId))) {
            weight = 1;
            return 0;
        }
        weight = 0;
        return  -1;
    }

    int32_t onFind(DocId docId, int32_t elemId) const override final {
        return ((elemId == 0) && (_invert != _bv.testBit(docId))) ? 0 : -1;
    }

public:
    BitVectorSearchContext(std::unique_ptr<QueryTermSimple> qTerm, const SingleBoolAttribute & bv);

    std::unique_ptr<queryeval::SearchIterator>
    createFilterIterator(fef::TermFieldMatchData * matchData, bool strict) override;
    void fetchPostings(const queryeval::ExecuteInfo &execInfo) override;
    std::unique_ptr<queryeval::SearchIterator> createPostingIterator(fef::TermFieldMatchData *matchData, bool strict) override;
    unsigned int approximateHits() const override;
};

BitVectorSearchContext::BitVectorSearchContext(std::unique_ptr<QueryTermSimple> qTerm, const SingleBoolAttribute & attr)
    : SearchContext(attr),
      _bv(attr.getBitVector()),
      _invert(false),
      _valid(qTerm->isValid())
{
    if ((strcmp("1", qTerm->getTerm()) == 0) || (strcasecmp("true", qTerm->getTerm()) == 0)) {
    } else if ((strcmp("0", qTerm->getTerm()) == 0) || (strcasecmp("false", qTerm->getTerm()) == 0)) {
        _invert = true;
    } else {
        _valid = false;
    }
    _plsc = this;
}

std::unique_ptr<queryeval::SearchIterator>
BitVectorSearchContext::createFilterIterator(fef::TermFieldMatchData * matchData, bool strict)
{
    if (!valid()) {
        return std::make_unique<queryeval::EmptySearch>();
    }
    return BitVectorIterator::create(&_bv, _attr.getCommittedDocIdLimit(), *matchData, strict, _invert);
}

void
BitVectorSearchContext::fetchPostings(const queryeval::ExecuteInfo &) {
}

std::unique_ptr<queryeval::SearchIterator>
BitVectorSearchContext::createPostingIterator(fef::TermFieldMatchData *matchData, bool strict) {
    return createFilterIterator(matchData, strict);
}

unsigned int
BitVectorSearchContext::approximateHits() const {
    return valid()
        ? (_invert)
            ? (_bv.size() - _bv.countTrueBits())
            : _bv.countTrueBits()
        : 0;
}

}

AttributeVector::SearchContext::UP
SingleBoolAttribute::getSearch(std::unique_ptr<QueryTermSimple> term, const attribute::SearchContextParams &) const {
    return std::make_unique<BitVectorSearchContext>(std::move(term), *this);
}

bool
SingleBoolAttribute::onLoad()
{
    PrimitiveReader<uint32_t> attrReader(*this);
    bool ok(attrReader.hasData());
    if (ok) {
        setCreateSerialNum(attrReader.getCreateSerialNum());
        getGenerationHolder().clearHoldLists();
        _bv.clear();
        uint32_t numDocs = attrReader.getNextData();
        _bv.extend(numDocs);
        ssize_t bytesRead = attrReader.getReader().read(_bv.getStart(), _bv.sizeBytes());
        _bv.invalidateCachedCount();
        _bv.countTrueBits();
        assert(bytesRead == _bv.sizeBytes());
        setNumDocs(numDocs);
        setCommittedDocIdLimit(numDocs);
    }

    return ok;
}

void
SingleBoolAttribute::onSave(IAttributeSaveTarget &saveTarget)
{
    assert(!saveTarget.getEnumerated());
    const size_t numDocs(getCommittedDocIdLimit());
    const size_t sz(sizeof(uint32_t) + _bv.sizeBytes());
    IAttributeSaveTarget::Buffer buf(saveTarget.datWriter().allocBuf(sz));

    char *p = buf->getFree();
    const char *e = p + sz;
    uint32_t numDocs2 = numDocs;
    memcpy(p, &numDocs2, sizeof(uint32_t));
    p += sizeof(uint32_t);
    memcpy(p, _bv.getStart(), _bv.sizeBytes());
    p += _bv.sizeBytes();
    assert(p == e);
    (void) e;
    buf->moveFreeToData(sz);
    saveTarget.datWriter().writeBuf(std::move(buf));
    assert(numDocs == getCommittedDocIdLimit());
}

void
SingleBoolAttribute::clearDocs(DocId lidLow, DocId lidLimit)
{
    assert(lidLow <= lidLimit);
    assert(lidLimit <= getNumDocs());
    for (DocId lid = lidLow; lid < lidLimit; ++lid) {
        if (getFast(lid) != 0) {
            clearDoc(lid);
        }
    }
}

void
SingleBoolAttribute::onShrinkLidSpace()
{
    uint32_t committedDocIdLimit = getCommittedDocIdLimit();
    assert(committedDocIdLimit < getNumDocs());
    _bv.shrink(committedDocIdLimit);
    setNumDocs(committedDocIdLimit);
}

uint64_t
SingleBoolAttribute::getEstimatedSaveByteSize() const
{
    constexpr uint64_t headerSize = 4_Ki + sizeof(uint32_t);
    return headerSize + _bv.sizeBytes();
}

void
SingleBoolAttribute::removeOldGenerations(generation_t firstUsed) {
    getGenerationHolder().trimHoldLists(firstUsed);
}

void
SingleBoolAttribute::onGenerationChange(generation_t generation) {
    getGenerationHolder().transferHoldLists(generation - 1);
}

}
