// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "singleboolattribute.h"
#include "attributevector.hpp"
#include "iattributesavetarget.h"
#include "ipostinglistsearchcontext.h"
#include "primitivereader.h"
#include "search_context.h"
#include "valuemodifier.h"
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/util/file_settings.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/util/size_literals.h>

namespace search {

using attribute::Config;

SingleBoolAttribute::
SingleBoolAttribute(const vespalib::string &baseFileName, const GrowStrategy & grow, bool paged)
    : IntegerAttributeTemplate<int8_t>(baseFileName, Config(BasicType::BOOL, CollectionType::SINGLE).setGrowStrategy(grow).setPaged(paged), BasicType::BOOL),
      _init_alloc(get_initial_alloc()),
      _bv(0, 0, getGenerationHolder(), get_memory_allocator() ? &_init_alloc : nullptr)
{
}

SingleBoolAttribute::~SingleBoolAttribute()
{
    getGenerationHolder().reclaim_all();
}

void
SingleBoolAttribute::ensureRoom(DocId docIdLimit) {
    if (_bv.writer().capacity() < docIdLimit) {
        const GrowStrategy & gs = this->getConfig().getGrowStrategy();
        uint32_t newSize = docIdLimit + (docIdLimit * gs.getGrowFactor()) + gs.getGrowDelta();
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
    reclaim_unused_memory();
    return true;
}

void
SingleBoolAttribute::onCommit() {
    checkSetMaxValueCount(1);

    if ( ! _changes.empty()) {
        // apply updates
        ValueModifier valueGuard(getValueModifier());
        for (const auto & change : _changes.getInsertOrder()) {
            if (change._type == ChangeBase::UPDATE) {
                std::atomic_thread_fence(std::memory_order_release);
                setBit(change._doc, change._data != 0);
            } else if ((change._type >= ChangeBase::ADD) && (change._type <= ChangeBase::DIV)) {
                std::atomic_thread_fence(std::memory_order_release);
                int8_t val = applyArithmetic<int8_t, largeint_t>(getFast(change._doc), change._data.getArithOperand(), change._type);
                setBit(change._doc, val != 0);
            } else if (change._type == ChangeBase::CLEARDOC) {
                std::atomic_thread_fence(std::memory_order_release);
                _bv.writer().clearBitAndMaintainCount(change._doc);
            }
        }
    }

    std::atomic_thread_fence(std::memory_order_release);
    reclaim_unused_memory();

    _changes.clear();
}

void
SingleBoolAttribute::onAddDocs(DocId docIdLimit) {
    ensureRoom(docIdLimit);
}

void
SingleBoolAttribute::onUpdateStat() {
    vespalib::MemoryUsage usage;
    usage.setAllocatedBytes(_bv.writer().extraByteSize());
    usage.setUsedBytes(_bv.writer().sizeBytes());
    usage.mergeGenerationHeldBytes(getGenerationHolder().get_held_bytes());
    usage.merge(this->getChangeVectorMemoryUsage());
    this->updateStatistics(_bv.writer().size(), _bv.writer().size(), usage.allocatedBytes(), usage.usedBytes(),
                           usage.deadBytes(), usage.allocatedBytesOnHold());
}

namespace {

class BitVectorSearchContext : public attribute::SearchContext, public attribute::IPostingListSearchContext
{
private:
    uint32_t _doc_id_limit;
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
    BitVectorSearchContext(std::unique_ptr<QueryTermSimple> qTerm, const SingleBoolAttribute & attr);

    std::unique_ptr<queryeval::SearchIterator>
    createFilterIterator(fef::TermFieldMatchData * matchData, bool strict) override;
    void fetchPostings(const queryeval::ExecuteInfo &execInfo) override;
    std::unique_ptr<queryeval::SearchIterator> createPostingIterator(fef::TermFieldMatchData *matchData, bool strict) override;
    unsigned int approximateHits() const override;
    uint32_t get_committed_docid_limit() const noexcept override;
};

BitVectorSearchContext::BitVectorSearchContext(std::unique_ptr<QueryTermSimple> qTerm, const SingleBoolAttribute & attr)
    : SearchContext(attr),
      _doc_id_limit(attr.getCommittedDocIdLimit()),
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
    return BitVectorIterator::create(&_bv, _doc_id_limit, *matchData, strict, _invert);
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

uint32_t
BitVectorSearchContext::get_committed_docid_limit() const noexcept
{
    return _doc_id_limit;
}

}

std::unique_ptr<attribute::SearchContext>
SingleBoolAttribute::getSearch(std::unique_ptr<QueryTermSimple> term, const attribute::SearchContextParams &) const {
    return std::make_unique<BitVectorSearchContext>(std::move(term), *this);
}

bool
SingleBoolAttribute::onLoad(vespalib::Executor *)
{
    PrimitiveReader<uint32_t> attrReader(*this);
    bool ok(attrReader.hasData());
    if (ok) {
        setCreateSerialNum(attrReader.getCreateSerialNum());
        getGenerationHolder().reclaim_all();
        _bv.writer().clear();
        uint32_t numDocs = attrReader.getNextData();
        _bv.extend(numDocs);
        ssize_t bytesRead = attrReader.getReader().read(_bv.writer().getStart(), _bv.writer().sizeBytes());
        _bv.writer().invalidateCachedCount();
        _bv.writer().countTrueBits();
        assert(bytesRead == _bv.writer().sizeBytes());
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
    const size_t sz(sizeof(uint32_t) + _bv.writer().sizeBytes());
    IAttributeSaveTarget::Buffer buf(saveTarget.datWriter().allocBuf(sz));

    char *p = buf->getFree();
    const char *e = p + sz;
    uint32_t numDocs2 = numDocs;
    memcpy(p, &numDocs2, sizeof(uint32_t));
    p += sizeof(uint32_t);
    memcpy(p, _bv.writer().getStart(), _bv.writer().sizeBytes());
    p += _bv.writer().sizeBytes();
    assert(p == e);
    (void) e;
    buf->moveFreeToData(sz);
    saveTarget.datWriter().writeBuf(std::move(buf));
    assert(numDocs == getCommittedDocIdLimit());
}

void
SingleBoolAttribute::clearDocs(DocId lidLow, DocId lidLimit, bool)
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
    constexpr uint64_t headerSize = FileSettings::DIRECTIO_ALIGNMENT + sizeof(uint32_t);
    return headerSize + _bv.reader().sizeBytes();
}

void
SingleBoolAttribute::reclaim_memory(generation_t oldest_used_gen) {
    getGenerationHolder().reclaim(oldest_used_gen);
}

void
SingleBoolAttribute::before_inc_generation(generation_t current_gen) {
    getGenerationHolder().assign_generation(current_gen);
}

}
