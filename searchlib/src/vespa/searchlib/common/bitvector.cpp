// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bitvector.h"
#include "allocatedbitvector.h"
#include "growablebitvector.h"
#include "partialbitvector.h"
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/fastos/file.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.common.bitvector");

using vespalib::make_string;
using vespalib::IllegalArgumentException;
using vespalib::hwaccelrated::IAccelrated;
using vespalib::Optimized;
using vespalib::alloc::Alloc;

namespace {

void verifyInclusiveStart(const search::BitVector & a, const search::BitVector & b) __attribute__((noinline));

void verifyInclusiveStart(const search::BitVector & a, const search::BitVector & b)
{
    if (a.getStartIndex() < b.getStartIndex()) {
        throw IllegalArgumentException(make_string("[%d, %d] starts before which is not allowed currently [%d, %d]",
                                                   a.getStartIndex(), a.size(), b.getStartIndex(), b.size()),
                                       VESPA_STRLOC);
    }
}

}

/////////////////////////////////
namespace search {

using vespalib::nbostream;
using vespalib::GenerationHeldBase;
using vespalib::GenerationHeldAlloc;
using vespalib::GenerationHolder;

Alloc
BitVector::allocatePaddedAndAligned(Index start, Index end, Index capacity)
{
    assert(capacity >= end);
    uint32_t words = numActiveWords(start, capacity);
    words += (-words & 15); // Pad to 64 byte alignment
    const size_t sz(words * sizeof(Word));
    Alloc alloc = Alloc::alloc(sz);
    assert(alloc.size()/sizeof(Word) >= words);
    // Clear padding
    size_t usedBytes = numBytes(end - start);
    memset(static_cast<char *>(alloc.get()) + usedBytes, 0, alloc.size() - usedBytes);
    return alloc;
}

BitVector::BitVector(void * buf, Index start, Index end) :
    _words(static_cast<Word *>(buf) - wordNum(start)),
    _startOffset(start),
    _sz(end),
    _numTrueBits(invalidCount())
{
    assert((reinterpret_cast<size_t>(_words) & (sizeof(Word) - 1ul)) == 0);
}

void
BitVector::init(void * buf,  Index start, Index end)
{
    _words = static_cast<Word *>(buf) - wordNum(start);
    _startOffset = start;
    _sz = end;
    _numTrueBits = invalidCount();
}

void
BitVector::clear()
{
    memset(getActiveStart(), '\0', getActiveBytes());
    setBit(size()); // Guard bit
    setTrueBits(0);
}

void
BitVector::clearInterval(Index start, Index end)
{
    clearIntervalNoInvalidation(Range(start, end));

    invalidateCachedCount();
}

void
BitVector::clearIntervalNoInvalidation(Range range_in)
{
    Range range = sanitize(range_in);
    if ( ! range.validNonZero()) { return; }

    Index last = range.end() - 1;
    Index startw = wordNum(range.start());
    Index endw = wordNum(last);

    if (endw > startw) {
        _words[startw++] &= startBits(range.start());
        memset(_words+startw, 0, sizeof(*_words)*(endw-startw));
        _words[endw] &= endBits(last);
    } else {
        _words[startw] &= (startBits(range.start()) | endBits(last));
    }
}

void
BitVector::setInterval(Index start_in, Index end_in)
{
    Range range = sanitize(Range(start_in, end_in));
    if ( ! range.validNonZero()) { return; }

    Index last = range.end() - 1;
    Index startw = wordNum(range.start());
    Index endw = wordNum(last);

    if (endw > startw) {
        _words[startw++] |= checkTab(range.start());
        memset(_words + startw, 0xff, sizeof(*_words)*(endw-startw));
        _words[endw] |= ~endBits(last);
    } else {
        _words[startw] |= ~(startBits(range.start()) | endBits(last));
    }

    invalidateCachedCount();
}

BitVector::Index
BitVector::count() const
{
    return countInterval(Range(getStartIndex(), size()));
}

BitVector::Index
BitVector::countInterval(Range range_in) const
{
    Range range = sanitize(range_in);
    if ( ! range.validNonZero()) { return 0; }

    Index last = range.end() - 1;
    // Count bits in range [start..end>
    Index startw = wordNum(range.start());
    Index endw = wordNum(last);
    Word *bitValues = _words;

    if (startw == endw) {
        return Optimized::popCount(bitValues[startw] & ~(startBits(range.start()) | endBits(last)));
    }
    Index res = 0;
    // Limit to full words
    if ((range.start() & (WordLen - 1)) != 0) {
        res += Optimized::popCount(bitValues[startw] & ~startBits(range.start()));
        ++startw;
    }
    // Align start to 16 bytes
    while (startw < endw && (startw & 3) != 0) {
        res += Optimized::popCount(bitValues[startw]);
        ++startw;
    }
    bool partialEnd = (last & (WordLen - 1)) != (WordLen - 1);
    if (!partialEnd) {
        ++endw;
    }
    if (startw < endw) {
        res += IAccelrated::getAccelerator().populationCount(bitValues + startw, endw - startw);
    }
    if (partialEnd) {
        res += Optimized::popCount(bitValues[endw] & ~endBits(last));
    }

    return res;
}

void
BitVector::orWith(const BitVector & right)
{
    verifyInclusiveStart(*this, right);

    if (right.size() < size()) {
        if (right.size() > 0) {
            ssize_t commonBytes = numActiveBytes(getStartIndex(), right.size()) - sizeof(Word);
            if (commonBytes > 0) {
                IAccelrated::getAccelerator().orBit(getActiveStart(), right.getWordIndex(getStartIndex()), commonBytes);
            }
            Index last(right.size() - 1);
            getWordIndex(last)[0] |= (right.getWordIndex(last)[0] & ~endBits(last));
        }
    } else {
        IAccelrated::getAccelerator().orBit(getActiveStart(), right.getWordIndex(getStartIndex()), getActiveBytes());
    }
    repairEnds();
    invalidateCachedCount();
}

void
BitVector::repairEnds()
{
    if (size() != 0) {
        Index start(getStartIndex());
        Index last(size() - 1);
        getWordIndex(start)[0] &= ~startBits(start);
        getWordIndex(last)[0] &= ~endBits(last);
    }
    setGuardBit();
}


void
BitVector::andWith(const BitVector & right)
{
    verifyInclusiveStart(*this, right);

    uint32_t commonBytes = std::min(getActiveBytes(), numActiveBytes(getStartIndex(), right.size()));
    IAccelrated::getAccelerator().andBit(getActiveStart(), right.getWordIndex(getStartIndex()), commonBytes);
    if (right.size() < size()) {
        clearInterval(right.size(), size());
    }

    repairEnds();
    invalidateCachedCount();
}


void
BitVector::andNotWith(const BitVector& right)
{
    verifyInclusiveStart(*this, right);

    if (right.size() < size()) {
        if (right.size() > 0) {
            ssize_t commonBytes = numActiveBytes(getStartIndex(), right.size()) - sizeof(Word);
            if (commonBytes > 0) {
                IAccelrated::getAccelerator().andNotBit(getActiveStart(), right.getWordIndex(getStartIndex()), commonBytes);
            }
            Index last(right.size() - 1);
            getWordIndex(last)[0] &= ~(right.getWordIndex(last)[0] & ~endBits(last));
        }
    } else {
        IAccelrated::getAccelerator().andNotBit(getActiveStart(), right.getWordIndex(getStartIndex()), getActiveBytes());
    }

    repairEnds();
    invalidateCachedCount();
}

void
BitVector::notSelf() {
    IAccelrated::getAccelerator().notBit(getActiveStart(), getActiveBytes());
    setGuardBit();
    invalidateCachedCount();
}

bool
BitVector::operator==(const BitVector &rhs) const
{
    if ((size() != rhs.size()) || (getStartIndex() != rhs.getStartIndex())) {
        return false;
    }

    Index bitVectorSize = numActiveWords();
    const Word *words = getActiveStart();
    const Word *oWords = rhs.getActiveStart();
    for (Index i = 0; i < bitVectorSize; i++) {
        if (words[i] != oWords[i]) {
            return false;
        }
    }
    return true;
}

bool
BitVector::hasTrueBitsInternal() const
{
    Index bitVectorSizeL1(numActiveWords() - 1);
    const Word *words(getActiveStart());
    for (Index i = 0; i < bitVectorSizeL1; i++) {
        if (words[i] != 0) {
            return true;
        }
    }

    // Ignore guard bit.
    if ((words[bitVectorSizeL1] & ~mask(size())) != 0)
        return true;

    return false;
}

//////////////////////////////////////////////////////////////////////
// Set new length. Destruction of content
//////////////////////////////////////////////////////////////////////
void
BitVector::resize(Index)
{
    LOG_ABORT("should not be reached");
}
GenerationHeldBase::UP
BitVector::grow(Index, Index )
{
    LOG_ABORT("should not be reached");
}

size_t
BitVector::getFileBytes(Index bits)
{
    Index bytes = numBytes(bits);
    bytes += (-bytes & (getAlignment() - 1));
    return bytes;
}

class MMappedBitVector : public BitVector
{
public:
    MMappedBitVector(Index numberOfElements, FastOS_FileInterface &file,
                     int64_t offset, Index doccount);

private:
    void read(Index numberOfElements, FastOS_FileInterface &file,
              int64_t offset, Index doccount);
};

BitVector::UP
BitVector::create(Index numberOfElements, FastOS_FileInterface &file,
                  int64_t offset, Index doccount)
{
    UP bv;
    if (file.IsMemoryMapped()) {
        bv = std::make_unique<MMappedBitVector>(numberOfElements, file, offset, doccount);
    } else {
        size_t padbefore, padafter;
        size_t vectorsize = getFileBytes(numberOfElements);
        file.DirectIOPadding(offset, vectorsize, padbefore, padafter);
        assert((padbefore & (getAlignment() - 1)) == 0);
        AllocatedBitVector::Alloc alloc = Alloc::alloc(padbefore + vectorsize + padafter, 0x1000000, 0x1000);
        void * alignedBuffer = alloc.get();
        file.ReadBuf(alignedBuffer, alloc.size(), offset - padbefore);
        bv = std::make_unique<AllocatedBitVector>(numberOfElements, std::move(alloc), padbefore);
        bv->setTrueBits(doccount);
        // Check guard bit for getNextTrueBit()
        assert(bv->testBit(bv->size()));
    }
    return bv;
}

BitVector::UP
BitVector::create(Index start, Index end)
{
    return (start == 0)
           ? create(end)
           : std::make_unique<PartialBitVector>(start, end);
}

BitVector::UP
BitVector::create(const BitVector & org, Index start, Index end)
{
    return ((start == 0) && (end == org.size()) && (org.getStartIndex() == 0))
           ? create(org)
           : std::make_unique<PartialBitVector>(org, start, end);
}

BitVector::UP
BitVector::create(Index numberOfElements)
{
    return std::make_unique<AllocatedBitVector>(numberOfElements);
}

BitVector::UP
BitVector::create(const BitVector & rhs)
{
    return std::make_unique<AllocatedBitVector>(rhs);
}

BitVector::UP
BitVector::create(Index numberOfElements, Index newCapacity, GenerationHolder &generationHolder)
{
    return std::make_unique<GrowableBitVector>(numberOfElements, newCapacity, generationHolder);
}

MMappedBitVector::MMappedBitVector(Index numberOfElements, FastOS_FileInterface &file,
                                   int64_t offset, Index doccount) :
    BitVector()
{
    read(numberOfElements, file, offset, doccount);
}

void
MMappedBitVector::read(Index numberOfElements, FastOS_FileInterface &file,
                       int64_t offset, Index doccount)
{
    assert((offset & (getAlignment() - 1)) == 0);
    void *mapptr = file.MemoryMapPtr(offset);
    assert(mapptr != nullptr);
    if (mapptr != nullptr) {
        init(mapptr, 0, numberOfElements);
    }
    setTrueBits(doccount);
}

nbostream &
operator<<(nbostream &out, const BitVector &bv)
{
    uint64_t size = bv.size();
    uint64_t cachedHits = bv.countTrueBits();
    uint64_t fileBytes = bv.getFileBytes();
    assert(size <= std::numeric_limits<BitVector::Index>::max());
    assert(cachedHits <= size || ! bv.isValidCount(cachedHits));
    assert(bv.testBit(size));
    out << size << cachedHits << fileBytes;
    out.write(bv.getStart(), bv.getFileBytes());
    return out;
}


nbostream &
operator>>(nbostream &in, BitVector &bv)
{
    uint64_t size;
    uint64_t cachedHits;
    uint64_t fileBytes;
    in >> size >> cachedHits >> fileBytes;
    assert(size <= std::numeric_limits<BitVector::Index>::max());
    assert(cachedHits <= size || ! bv.isValidCount(cachedHits));
    if (bv.size() != size)
        bv.resize(size);
    assert(bv.getFileBytes() == fileBytes);
    in.read(bv.getStart(), bv.getFileBytes());
    assert(bv.testBit(size));
    bv.setTrueBits(cachedHits);
    return in;
}


} // namespace search
