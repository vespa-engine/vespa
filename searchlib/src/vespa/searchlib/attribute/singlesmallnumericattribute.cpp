// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "singlesmallnumericattribute.h"
#include "attributevector.hpp"
#include "iattributesavetarget.h"
#include "primitivereader.h"
#include "single_small_numeric_search_context.h"
#include "valuemodifier.h"
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/util/file_settings.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/util/size_literals.h>

namespace search {

SingleValueSmallNumericAttribute::
SingleValueSmallNumericAttribute(const vespalib::string & baseFileName,
                                 const Config & c,
                                 Word valueMask,
                                 uint32_t valueShiftShift,
                                 uint32_t valueShiftMask,
                                 uint32_t wordShift)
    : B(baseFileName, c, c.basicType()),
      _valueMask(valueMask),
      _valueShiftShift(valueShiftShift),
      _valueShiftMask(valueShiftMask),
      _wordShift(wordShift),
      _wordData(c.getGrowStrategy(), getGenerationHolder())
{
    assert(_valueMask + 1 == (1u << (1u << valueShiftShift)));
    assert((_valueShiftMask + 1) * (1u << valueShiftShift) == 8 * sizeof(Word));
    assert(_valueShiftMask + 1 == (1u << wordShift));
}


SingleValueSmallNumericAttribute::~SingleValueSmallNumericAttribute()
{
    getGenerationHolder().reclaim_all();
}

void
SingleValueSmallNumericAttribute::onAddDocs(DocId lidLimit) {
    _wordData.reserve((lidLimit >> _wordShift) + 1);
}

void
SingleValueSmallNumericAttribute::onCommit()
{
    checkSetMaxValueCount(1);

    {
        // apply updates
        B::ValueModifier valueGuard(getValueModifier());
        for (const auto & change : _changes.getInsertOrder()) {
            if (change._type == ChangeBase::UPDATE) {
                std::atomic_thread_fence(std::memory_order_release);
                set(change._doc, change._data);
            } else if (change._type >= ChangeBase::ADD && change._type <= ChangeBase::DIV) {
                std::atomic_thread_fence(std::memory_order_release);
                set(change._doc, applyArithmetic<T, typename Change::DataType>(getFast(change._doc), change._data.getArithOperand(), change._type));
            } else if (change._type == ChangeBase::CLEARDOC) {
                std::atomic_thread_fence(std::memory_order_release);
                set(change._doc, 0u);
            }
        }
    }

    std::atomic_thread_fence(std::memory_order_release);
    reclaim_unused_memory();

    _changes.clear();
}

bool
SingleValueSmallNumericAttribute::addDoc(DocId & doc) {
    if ((B::getNumDocs() & _valueShiftMask) == 0) {
        bool incGen = _wordData.isFull();
        _wordData.push_back(Word());
        std::atomic_thread_fence(std::memory_order_release);
        B::incNumDocs();
        doc = B::getNumDocs() - 1;
        updateUncommittedDocIdLimit(doc);
        if (incGen) {
            this->incGeneration();
        } else
            this->reclaim_unused_memory();
    } else {
        B::incNumDocs();
        doc = B::getNumDocs() - 1;
        updateUncommittedDocIdLimit(doc);
    }
    return true;
}

void
SingleValueSmallNumericAttribute::onUpdateStat()
{
    vespalib::MemoryUsage usage = _wordData.getMemoryUsage();
    usage.mergeGenerationHeldBytes(getGenerationHolder().get_held_bytes());
    uint32_t numDocs = B::getNumDocs();
    updateStatistics(numDocs, numDocs,
                     usage.allocatedBytes(), usage.usedBytes(),
                     usage.deadBytes(), usage.allocatedBytesOnHold());
}


void
SingleValueSmallNumericAttribute::reclaim_memory(generation_t oldest_used_gen)
{
    getGenerationHolder().reclaim(oldest_used_gen);
}


void
SingleValueSmallNumericAttribute::before_inc_generation(generation_t current_gen)
{
    getGenerationHolder().assign_generation(current_gen);
}


bool
SingleValueSmallNumericAttribute::onLoad(vespalib::Executor *)
{
    PrimitiveReader<Word> attrReader(*this);
    bool ok(attrReader.hasData());
    if (ok) {
        setCreateSerialNum(attrReader.getCreateSerialNum());
        const size_t sz(attrReader.getDataCount());
        getGenerationHolder().reclaim_all();
        _wordData.reset();
        _wordData.unsafe_reserve(sz - 1);
        Word numDocs = attrReader.getNextData();
        for (uint32_t i = 1; i < sz; ++i) {
            _wordData.push_back(attrReader.getNextData());
        }
        assert(((numDocs + _valueShiftMask) >> _wordShift) + 1 == sz);
        B::setNumDocs(numDocs);
        B::setCommittedDocIdLimit(numDocs);
    }

    return ok;
}


void
SingleValueSmallNumericAttribute::onSave(IAttributeSaveTarget &saveTarget)
{
    assert(!saveTarget.getEnumerated());
    const size_t numDocs(getCommittedDocIdLimit());
    const size_t numDataWords((numDocs + _valueShiftMask) >> _wordShift);
    const size_t sz((numDataWords + 1) * sizeof(Word));
    IAttributeSaveTarget::Buffer buf(saveTarget.datWriter().allocBuf(sz));

    char *p = buf->getFree();
    const char *e = p + sz;
    Word numDocs2 = numDocs;
    memcpy(p, &numDocs2, sizeof(Word));
    p += sizeof(Word);
    memcpy(p, &_wordData[0], numDataWords * sizeof(Word));
    p += numDataWords * sizeof(Word);
    assert(p == e);
    (void) e;
    buf->moveFreeToData(sz);
    saveTarget.datWriter().writeBuf(std::move(buf));
    assert(numDocs == getCommittedDocIdLimit());
}

std::unique_ptr<attribute::SearchContext>
SingleValueSmallNumericAttribute::getSearch(std::unique_ptr<QueryTermSimple> qTerm,
                                            const attribute::SearchContextParams &) const
{
    return std::make_unique<attribute::SingleSmallNumericSearchContext>(std::move(qTerm), *this, &_wordData.acquire_elem_ref(0), _valueMask, _valueShiftShift, _valueShiftMask, _wordShift);
}

void
SingleValueSmallNumericAttribute::clearDocs(DocId lidLow, DocId lidLimit, bool)
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
SingleValueSmallNumericAttribute::onShrinkLidSpace()
{
    uint32_t committedDocIdLimit = getCommittedDocIdLimit();
    assert(committedDocIdLimit < getNumDocs());
    const size_t numDocs(committedDocIdLimit);
    const size_t numDataWords((numDocs + _valueShiftMask) >> _wordShift);
    _wordData.shrink(numDataWords);
    setNumDocs(committedDocIdLimit);
}

uint64_t
SingleValueSmallNumericAttribute::getEstimatedSaveByteSize() const
{
    uint64_t headerSize = FileSettings::DIRECTIO_ALIGNMENT;
    const size_t numDocs(getCommittedDocIdLimit());
    const size_t numDataWords((numDocs + _valueShiftMask) >> _wordShift);
    const size_t sz((numDataWords + 1) * sizeof(Word));
    return headerSize + sz;
}

namespace {

template <typename TT>
uint32_t
log2bits();

template <>
uint32_t
log2bits<uint32_t>()
{
    return 0x05u;
}

using search::attribute::Config;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::GrowStrategy;

Config
createConfig(BasicType bt, CollectionType ct) {
    return Config(bt, ct);
}
Config
createConfig(BasicType bt, CollectionType ct, const GrowStrategy & grow) {
    return createConfig(bt, ct).setGrowStrategy(grow);
}

}

SingleValueSemiNibbleNumericAttribute::
SingleValueSemiNibbleNumericAttribute(const vespalib::string &baseFileName, const search::GrowStrategy & grow)
    : SingleValueSmallNumericAttribute(baseFileName,
                                       createConfig(BasicType::UINT2, CollectionType::SINGLE, grow),
                                       0x03u /* valueMask */,
                                       0x01u /* valueShiftShift */,
                                       4 * sizeof(Word) - 1 /* valueShiftMask */,
                                       log2bits<Word>() - 1/* wordShift */)
{
}


SingleValueNibbleNumericAttribute::
SingleValueNibbleNumericAttribute(const vespalib::string &baseFileName, const search::GrowStrategy & grow)
    : SingleValueSmallNumericAttribute(baseFileName,
                                       createConfig(BasicType::UINT4, CollectionType::SINGLE, grow),
                                       0x0fu /* valueMask */,
                                       0x02u /* valueShiftShift */,
                                       2 * sizeof(Word) - 1 /* valueShiftMask */,
                                       log2bits<Word>() - 2/* wordShift */)
{
}

}
