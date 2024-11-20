// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bitvector.h"
#include "allocatedbitvector.h"
#include "partialbitvector.h"
#include "read_stats.h"
#include <vespa/searchlib/util/file_settings.h>
#include <vespa/vespalib/hwaccelerated/iaccelerated.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/thread_bundle.h>
#include <vespa/vespalib/util/round_up_to_page_size.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/fastos/file.h>
#include <cassert>
#include <cstdlib>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.common.bitvector");

using vespalib::make_string;
using vespalib::IllegalArgumentException;
using vespalib::hwaccelerated::IAccelerated;
using vespalib::alloc::Alloc;

namespace {

constexpr size_t MMAP_LIMIT = 256_Mi;

}

/////////////////////////////////
namespace search {

using vespalib::nbostream;

bool BitVector::_enable_range_check = false;


struct BitVector::OrParts : vespalib::Runnable
{
    OrParts(std::span<BitVector* const> vectors, BitVector::Index offset, BitVector::Index size) noexcept
        : _vectors(vectors),
          _offset(offset),
          _byte_size((size + 7)/8)
    {}
    void run() override {
        const auto & accelrator = IAccelerated::getAccelerator();
        BitVector * master = _vectors[0];
        Word * destination = master->getWordIndex(_offset);
        for (uint32_t i(1); i < _vectors.size(); i++) {
            accelrator.orBit(destination, _vectors[i]->getWordIndex(_offset), _byte_size);
        }
    }
    std::span<BitVector* const> _vectors;
    BitVector::Index _offset;
    BitVector::Index _byte_size;
};

void
BitVector::parallellOr(vespalib::ThreadBundle & thread_bundle, std::span<BitVector* const> vectors) {
    constexpr uint32_t MIN_BITS_PER_THREAD = 128_Ki;
    constexpr uint32_t ALIGNMENT_BITS = 8_Ki;
    if (vectors.size() < 2) return;
    BitVector * master = vectors[0];
    Index size = master->size();
    size_t max_num_chunks = (size + (MIN_BITS_PER_THREAD - 1)) / MIN_BITS_PER_THREAD;
    size_t max_threads = std::max(1ul, std::min(thread_bundle.size(), max_num_chunks));

    if (max_threads < 2) {
        for (uint32_t i(1); i < vectors.size(); i++) {
            master->orWith(*vectors[i]);
        }
    } else {
        for (const BitVector *bv: vectors) {
            assert(bv->getStartIndex() == 0u);
            assert(bv->size() == size);
        }
        std::vector<BitVector::OrParts> parts;
        parts.reserve(max_threads);
        uint32_t bits_per_thread = ((size/max_threads)/ALIGNMENT_BITS) * ALIGNMENT_BITS;
        Index offset = 0;
        for (uint32_t i(0); (i + 1) < max_threads; i++) {
            parts.emplace_back(vectors, offset, bits_per_thread);
            offset += bits_per_thread;
        }
        parts.emplace_back(vectors, offset, size - offset);
        thread_bundle.run(parts);
        master->repairEnds();
    }
}

Alloc
BitVector::allocatePaddedAndAligned(Index start, Index end, Index capacity, const Alloc* init_alloc)
{
    assert(capacity >= end);
    uint32_t words = numActiveWords(start, capacity);
    words += (-words & (getAlignment()/sizeof(Word) - 1)); // Pad to required alignment
    const size_t sz(words * sizeof(Word));
    Alloc alloc = (init_alloc != nullptr) ? init_alloc->create(sz) : Alloc::alloc(sz, MMAP_LIMIT);
    assert(alloc.size()/sizeof(Word) >= words);
    // Clear padding
    size_t usedBytes = numBytes(end - start);
    memset(static_cast<char *>(alloc.get()) + usedBytes, 0, alloc.size() - usedBytes);
    return alloc;
}

BitVector::BitVector(void * buf, Index start, Index end) noexcept :
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
    set_bit_no_range_check(size()); // Guard bit
    setTrueBits(0);
}

void
BitVector::clearInterval(Index start, Index end)
{
    clearIntervalNoInvalidation(Range(start, end));
    invalidateCachedCount();
}

void
BitVector::store(Word &word, Word value) {
    assert(!_enable_range_check || ((&word >= getActiveStart()) && (&word < (getActiveStart() + numActiveWords()))));
    return store_unchecked(word, value);
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
        store(_words[startw], _words[startw] & startBits(range.start()));
        for (Index i = startw + 1; i < endw; ++i) {
            store_unchecked(_words[i], 0);
        }
        store(_words[endw], _words[endw] & endBits(last));
    } else {
        store(_words[startw], _words[startw] & (startBits(range.start()) | endBits(last)));
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
        store(_words[startw], _words[startw] | checkTab(range.start()));
        for (Index i = startw + 1; i < endw; ++i) {
            store_unchecked(_words[i], allBits());
        }
        store(_words[endw], _words[endw] | ~endBits(last));
    } else {
        store(_words[startw], _words[startw] | ~(startBits(range.start()) | endBits(last)));
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
        return std::popcount(load(bitValues[startw]) & ~(startBits(range.start()) | endBits(last)));
    }
    Index res = 0;
    // Limit to full words
    if ((range.start() & (WordLen - 1)) != 0) {
        res += std::popcount(load(bitValues[startw]) & ~startBits(range.start()));
        ++startw;
    }
    // Align start to 16 bytes
    while (startw < endw && (startw & 3) != 0) {
        res += std::popcount(load(bitValues[startw]));
        ++startw;
    }
    bool partialEnd = (last & (WordLen - 1)) != (WordLen - 1);
    if (!partialEnd) {
        ++endw;
    }
    if (startw < endw) {
        res += IAccelerated::getAccelerator().populationCount(bitValues + startw, endw - startw);
    }
    if (partialEnd) {
        res += std::popcount(load(bitValues[endw]) & ~endBits(last));
    }

    return res;
}

void
BitVector::orWith(const BitVector & right)
{
    Range range = sanitize(right.range());
    if ( ! range.validNonZero()) return;

    if (right.size() < size()) {
        ssize_t commonBytes = numActiveBytes(range.start(), range.end()) - sizeof(Word);
        if (commonBytes > 0) {
            IAccelerated::getAccelerator().orBit(getWordIndex(range.start()), right.getWordIndex(range.start()), commonBytes);
        }
        Index last(range.end() - 1);
        store(getWordIndex(last)[0], getWordIndex(last)[0] | (load(right.getWordIndex(last)[0]) & ~endBits(last)));
    } else {
        IAccelerated::getAccelerator().orBit(getWordIndex(range.start()), right.getWordIndex(range.start()), getActiveBytes());
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
        store(getWordIndex(start)[0], getWordIndex(start)[0] & ~startBits(start));
        store(getWordIndex(last)[0], getWordIndex(last)[0] & ~endBits(last));
    }
    setGuardBit();
}


void
BitVector::andWith(const BitVector & right)
{
    Range range = sanitize(right.range());
    if ( ! range.validNonZero()) {
        clear();
        return;
    }

    uint32_t commonBytes = std::min(getActiveBytes(), numActiveBytes(getStartIndex(), right.size()));
    IAccelerated::getAccelerator().andBit(getActiveStart(), right.getWordIndex(getStartIndex()), commonBytes);
    if (right.size() < size()) {
        clearInterval(right.size(), size());
    }

    repairEnds();
    invalidateCachedCount();
}


void
BitVector::andNotWith(const BitVector& right)
{
    Range range = sanitize(right.range());
    if ( ! range.validNonZero()) return;

    if (right.size() < size()) {
        ssize_t commonBytes = numActiveBytes(range.start(), range.end()) - sizeof(Word);
        if (commonBytes > 0) {
            IAccelerated::getAccelerator().andNotBit(getWordIndex(range.start()), right.getWordIndex(range.start()), commonBytes);
        }
        Index last(range.end() - 1);
        store(getWordIndex(last)[0], getWordIndex(last)[0] & ~(load(right.getWordIndex(last)[0]) & ~endBits(last)));
    } else {
        IAccelerated::getAccelerator().andNotBit(getWordIndex(range.start()), right.getWordIndex(range.start()), getActiveBytes());
    }

    repairEnds();
    invalidateCachedCount();
}

void
BitVector::notSelf() {
    IAccelerated::getAccelerator().notBit(getActiveStart(), getActiveBytes());
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
        if (load(words[i]) != load(oWords[i])) {
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
        if (load(words[i]) != 0) {
            return true;
        }
    }

    // Ignore guard bit.
    if ((load(words[bitVectorSizeL1]) & ~mask(size())) != 0)
        return true;

    return false;
}

//////////////////////////////////////////////////////////////////////

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

    size_t get_allocated_bytes(bool include_self) const noexcept override;

private:
    void read(Index numberOfElements, FastOS_FileInterface &file,
              int64_t offset, Index doccount);
};

BitVector::UP
BitVector::create(Index numberOfElements, FastOS_FileInterface &file,
                  int64_t offset, Index doccount, ReadStats& read_stats)
{
    UP bv;
    if (file.IsMemoryMapped()) {
        size_t pad_before = offset - vespalib::round_down_to_page_boundary(offset);
        read_stats.read_bytes = vespalib::round_up_to_page_size(pad_before + getFileBytes(numberOfElements));
        bv = std::make_unique<MMappedBitVector>(numberOfElements, file, offset, doccount);
    } else {
        size_t padbefore, padafter;
        size_t vectorsize = getFileBytes(numberOfElements);
        file.DirectIOPadding(offset, vectorsize, padbefore, padafter);
        assert((padbefore & (getAlignment() - 1)) == 0);
        AllocatedBitVector::Alloc alloc = Alloc::alloc(padbefore + vectorsize + padafter,
                                                       MMAP_LIMIT, FileSettings::DIRECTIO_ALIGNMENT);
        void * alignedBuffer = alloc.get();
        file.ReadBuf(alignedBuffer, padbefore + vectorsize + padafter, offset - padbefore);
        read_stats.read_bytes = padbefore + vectorsize + padafter;
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

void
BitVector::consider_enable_range_check()
{
    const char *env = getenv("VESPA_BITVECTOR_RANGE_CHECK");
    if (env != nullptr && strcmp(env, "true") == 0) {
        _enable_range_check = true;
    }
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
    void *mapptr = file.MemoryMapPtr(offset);
    assert(mapptr != nullptr);
    if (mapptr != nullptr) {
        init(mapptr, 0, numberOfElements);
    }
    setTrueBits(doccount);
}

size_t
MMappedBitVector::get_allocated_bytes(bool include_self) const noexcept
{
    return include_self ? sizeof(MMappedBitVector) : 0;
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
operator>>(nbostream &in, AllocatedBitVector &bv)
{
    uint64_t size;
    uint64_t cachedHits;
    uint64_t fileBytes;
    in >> size >> cachedHits >> fileBytes;
    assert(size <= std::numeric_limits<BitVector::Index>::max());
    assert(cachedHits <= size || ! bv.isValidCount(cachedHits));
    if (bv.size() != size) {
        bv.resize(size);
    }
    size_t expected_file_bytes = bv.getFileBytes();
    size_t read_size = fileBytes;
    size_t skip_size = 0;
    if (expected_file_bytes < fileBytes) {
        read_size = expected_file_bytes;
        skip_size = fileBytes - expected_file_bytes;
    }
    in.read(bv.getStart(), read_size);
    if (skip_size != 0) {
        std::vector<char> dummy(skip_size);
        in.read(dummy.data(), skip_size);
    }
    assert(bv.testBit(size));
    bv.setTrueBits(cachedHits);
    return in;
}

class ConsiderEnableRangeCheckCaller
{
public:
    ConsiderEnableRangeCheckCaller();
};

ConsiderEnableRangeCheckCaller::ConsiderEnableRangeCheckCaller()
{
    BitVector::consider_enable_range_check();
}

ConsiderEnableRangeCheckCaller consider_enable_range_check_caller;

} // namespace search
