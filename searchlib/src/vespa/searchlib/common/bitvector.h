// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bitword.h"
#include <memory>
#include <vespa/vespalib/util/alloc.h>
#include <vespa/vespalib/util/atomic.h>
#include <vespa/fastos/types.h>
#include <algorithm>
#include <cassert>

namespace vespalib {
    class nbostream;
}

class FastOS_FileInterface;

namespace search {

class PartialBitVector;
class AllocatedBitVector;

class BitVector : protected BitWord
{
public:
    using Index = BitWord::Index;
    using UP = std::unique_ptr<BitVector>;
    class Range {
    public:
        Range(Index start_in, Index end_in) noexcept : _start(start_in), _end(end_in) {}
        [[nodiscard]] Index start() const noexcept { return _start; }
        [[nodiscard]] Index end() const noexcept { return _end; }
        [[nodiscard]] bool validNonZero() const noexcept { return _end > _start; }
    private:
        Index _start;
        Index _end;
    };
    BitVector(const BitVector &) = delete;
    BitVector& operator = (const BitVector &) = delete;
    virtual ~BitVector() = default;
    bool operator == (const BitVector &right) const;
    const void * getStart() const { return _words; }
    void * getStart() { return _words; }
    Range range() const noexcept { return {getStartIndex(), size()}; }
    Index size() const { return vespalib::atomic::load_ref_relaxed(_sz); }
    Index sizeBytes() const { return numBytes(getActiveSize()); }
    bool testBit(Index idx) const {
        return ((load(_words[wordNum(idx)]) & mask(idx)) != 0);
    }
    Index getSizeAcquire() const {
        return vespalib::atomic::load_ref_acquire(_sz);
    }
    bool testBitAcquire(Index idx) const {
        auto my_word = vespalib::atomic::load_ref_acquire(_words[wordNum(idx)]);
        return (my_word & mask(idx)) != 0;
    }
    bool hasTrueBits() const {
        return isValidCount()
            ? (countTrueBits() != 0)
            : hasTrueBitsInternal();
    }
    Index countTrueBits() const {
        if ( ! isValidCount()) {
            updateCount();
        }
        return _numTrueBits.load(std::memory_order_relaxed);
    }

    /**
     * Will provide the first valid bit of the bitvector.
     *
     * @return The Index of the first valid bit of the bitvector.
     */
    Index getStartIndex() const { return _startOffset; }

    /**
     * Get next bit set in the bitvector (inclusive start).
     * It assumes that bitvector is non-zero terminated.
     *
     * @param start first bit to check
     * @return next bit set in the bitvector.
     */
    Index getNextTrueBit(Index start) const {
        return getNextBit([](Word w) { return w; }, start);
    }
    Index getNextFalseBit(Index start) const {
        return getNextBit([](Word w) { return ~w; }, start);
    }

    /**
     * Iterate over all true bits in the inclusive range.
     *
     * @param func callback
     * @param start first bit
     * @param last bit
     */
    template <typename FunctionType>
    void
    foreach_truebit(FunctionType func, Index start=0, Index end=std::numeric_limits<Index>::max()) const {
        foreach(func, [](Word w) { return w; }, start, end);
    }

    /**
     * Iterate over all true bits in the inclusive range.
     *
     * @param func callback
     * @param start first bit
     * @param last bit
     */
    template <typename FunctionType>
    void
    foreach_falsebit(FunctionType func, Index start=0, Index end=std::numeric_limits<Index>::max()) const {
        foreach(func, [](Word w) { return ~w; }, start, end);
    }

    Index getFirstTrueBit(Index start=0) const {
        return getNextTrueBit(std::max(start, getStartIndex()));
    }
    Index getFirstFalseBit(Index start=0) const {
        return getNextFalseBit(std::max(start, getStartIndex()));
    }

    Index getPrevTrueBit(Index start) const {
        Index index(wordNum(start));
        const Word *words(_words);
        Word t(load(words[index]) & ~endBits(start));

        while(t == 0 && index > getStartWordNum()) {
            t = load(words[--index]);
        }

        return (t != 0)
            ? (index << numWordBits()) + vespalib::Optimized::msbIdx(t)
            : getStartIndex();
    }

    void setSize(Index sz) {
        set_bit_no_range_check(sz);  // Need to place the new stop sign first
        std::atomic_thread_fence(std::memory_order_release);
        if (sz > _sz) {
            // Can only remove the old stopsign if it is ahead of the new.
            clear_bit_no_range_check(_sz);
        }
        vespalib::atomic::store_ref_release(_sz, sz);
    }
    void set_bit_no_range_check(Index idx) {
        store_unchecked(_words[wordNum(idx)], _words[wordNum(idx)] | mask(idx));
    }
    void clear_bit_no_range_check(Index idx) {
        store_unchecked(_words[wordNum(idx)], _words[wordNum(idx)] & ~ mask(idx));
    }
    void flip_bit_no_range_check(Index idx) {
        store_unchecked(_words[wordNum(idx)], _words[wordNum(idx)] ^ mask(idx));
    }
    void range_check(Index idx) const {
        assert(!_enable_range_check || (idx >= _startOffset && idx < _sz));
    }
    void setBit(Index idx) {
        range_check(idx);
        set_bit_no_range_check(idx);
    }
    void clearBit(Index idx) {
        range_check(idx);
        clear_bit_no_range_check(idx);
    }
    void flipBit(Index idx) {
        range_check(idx);
        flip_bit_no_range_check(idx);
    }

    void andWith(const BitVector &right);
    void orWith(const BitVector &right);
    void andNotWith(const BitVector &right);
    void notSelf();

    /**
     * Clear all bits in the bit vector.
     */
    void clear();

    /**
     * Clear a sequence of bits [..>.
     *
     * @param start first bit to be cleared
     * @param end limit
     */
    void clearInterval(Index start, Index end);
    /**
     * Set a sequence of bits.
     *
     * @param start first bit to be set [..>
     * @param end limit
     */
    void setInterval(Index start, Index end);

    /**
     * Sets a bit and maintains count of number of bits set.
     * @param idx
     */
    void setBitAndMaintainCount(Index idx) {
        if ( ! testBit(idx) ) {
            setBit(idx);
            incNumBits();
        }
    }

    void clear_bit_and_maintain_count_no_range_check(Index idx) {
        if (testBit(idx)) {
            clear_bit_no_range_check(idx);
            decNumBits();
        }
    }

    /**
     * Clears a bit and maintains count of number of bits set.
     * @param idx
     */
    void clearBitAndMaintainCount(Index idx) {
        if (testBit(idx)) {
            clearBit(idx);
            decNumBits();
        }
    }

    /**
     * Invalidate cached count of bits set in bit vector.  This method
     * should be called before calling Test/Clear/Flip methods.
     */
    void invalidateCachedCount() const {
        _numTrueBits.store(invalidCount(), std::memory_order_relaxed);
    }

    /**
     * Count bits in partial bitvector [..>.
     *
     * @param start first bit to be counted
     * @param end limit
     */
    Index countInterval(Index start, Index end) const {
        return countInterval(Range(start, end));
    }
    Index countInterval(Range range) const;

    /**
     * Perform an andnot with an internal array representation.
     *
     * @param other internal array representation
     * @param otherCount number of elements in array
     */
    template <typename T>
    void andNotWithT(T it);

    /*
     * Calculate the size of a bitmap when performing file io.
     */
    static size_t getFileBytes(Index bits);

    /*
     * Calculate the size of a bitmap when performing file io.
     */
    size_t getFileBytes() const {
        return getFileBytes(size());
    }

    /**
     * This will create the appropriate vector.
     *
     * @param numberOfElements  The size of the bit vector in bits.
     * @param file              The file from which to read the bit vector.
     * @param offset            Where bitvector image is located in the file.
     * @param doccount          Number of bits set in bitvector
     */
    static UP create(Index numberOfElements, FastOS_FileInterface &file, int64_t offset, Index doccount);
    static UP create(Index start, Index end);
    static UP create(const BitVector & org, Index start, Index end);
    static UP create(Index numberOfElements);
    static UP create(const BitVector & rhs);
    static void consider_enable_range_check();
protected:
    using Alloc = vespalib::alloc::Alloc;
    VESPA_DLL_LOCAL BitVector(void * buf, Index start, Index end);
    BitVector(void * buf, Index sz) : BitVector(buf, 0, sz) { }
    BitVector() : BitVector(nullptr, 0) { }
    void init(void * buf,  Index start, Index end);
    void updateCount() const { _numTrueBits.store(count(), std::memory_order_relaxed); }
    void setTrueBits(Index numTrueBits) { _numTrueBits.store(numTrueBits, std::memory_order_relaxed); }
    VESPA_DLL_LOCAL void clearIntervalNoInvalidation(Range range);
    bool isValidCount() const { return isValidCount(_numTrueBits.load(std::memory_order_relaxed)); }
    static bool isValidCount(Index v) { return v != invalidCount(); }
    static Index numWords(Index bits) { return wordNum(bits + 1 + (WordLen - 1)); }
    static Index numBytes(Index bits) { return numWords(bits) * sizeof(Word); }
    size_t numWords() const { return numWords(size()); }
    static size_t getAlignment() { return 0x40u; }
    static size_t numActiveBytes(Index start, Index end) { return numActiveWords(start, end) * sizeof(Word); }
    static Alloc allocatePaddedAndAligned(Index sz) {
        return allocatePaddedAndAligned(0, sz);
    }
    static Alloc allocatePaddedAndAligned(Index start, Index end) {
        return allocatePaddedAndAligned(start, end, end);
    }
    static Alloc allocatePaddedAndAligned(Index start, Index end, Index capacity, const Alloc* init_alloc = nullptr);

private:
    static Word load(const Word &word) { return vespalib::atomic::load_ref_relaxed(word); }
    VESPA_DLL_LOCAL void store(Word &word, Word value);
    static void store_unchecked(Word &word, Word value) {
        return vespalib::atomic::store_ref_relaxed(word, value);
    }
    friend PartialBitVector;
    const Word * getWordIndex(Index index) const { return static_cast<const Word *>(getStart()) + wordNum(index); }
    Word * getWordIndex(Index index) { return static_cast<Word *>(getStart()) + wordNum(index); }
    const Word * getActiveStart() const { return getWordIndex(getStartIndex()); }
    Word * getActiveStart() { return getWordIndex(getStartIndex()); }
    Index getStartWordNum() const { return wordNum(getStartIndex()); }
    Index getActiveSize() const { return size() - getStartIndex(); }
    size_t getActiveBytes() const { return numActiveBytes(getStartIndex(), size()); }
    size_t numActiveWords() const { return numActiveWords(getStartIndex(), size()); }
    static size_t numActiveWords(Index start, Index end) {
        return (end >= start) ? (numWords(end) - wordNum(start)) : 0;
    }
    static Index invalidCount() { return std::numeric_limits<Index>::max(); }
    void setGuardBit() { set_bit_no_range_check(size()); }
    void incNumBits() {
        if ( isValidCount() ) {
            _numTrueBits.store(_numTrueBits.load(std::memory_order_relaxed) + 1, std::memory_order_relaxed);
        }
    }
    void decNumBits() {
        if ( isValidCount() ) {
            _numTrueBits.store(_numTrueBits.load(std::memory_order_relaxed) - 1, std::memory_order_relaxed);

        }
    }
    VESPA_DLL_LOCAL void repairEnds();
    Range sanitize(Range range) const {
        return {std::max(range.start(), getStartIndex()),
                std::min(range.end(), size())};
    }
    Index count() const;
    bool hasTrueBitsInternal() const;
    template <typename FunctionType, typename WordConverter>
    void
    foreach(FunctionType func, WordConverter conv, Index start, Index end) const
    {
        if ((end <= start) || (size() == 0)) return;
        Index last = std::min(end, size()) - 1;
        if (start < getStartIndex()) start = getStartIndex();

        Index index(wordNum(start));
        Index lastIndex(wordNum(last));
        Word word(conv(load(_words[index])) & checkTab(start));
        for ( ; index < lastIndex; word = conv(load(_words[++index]))) {
            foreach_bit(func, word, index << numWordBits());
        }
        foreach_bit(func, word & ~endBits(last), lastIndex << numWordBits());
    }
    template<typename WordConverter>
    Index getNextBit(WordConverter conv, Index start) const {
        Index index(wordNum(start));
        const Word *words(_words);
        Word t(conv(load(words[index])) & checkTab(start));

        // In order to avoid a test an extra guard bit is added
        // after the bitvector as a termination.
        // Also bitvector will normally at least 1 bit set per 32 bits.
        // So that is what we should expect.
        while (__builtin_expect(t == 0, false)) {
            t = conv(load(words[++index]));
        }

        return (index << numWordBits()) + vespalib::Optimized::lsbIdx(t);
    }
    template <typename FunctionType>
    static void
    foreach_bit(FunctionType func, Word word, Index start)
    {
        while (word) {
            uint32_t pos = vespalib::Optimized::lsbIdx(word);
            func(start+pos);
            start += pos + 1;
            word >>= pos;
            word >>= 1u;
        }
    }


    Word          *_words;        // This is the buffer staring at Index 0
    Index          _startOffset;  // This is the official start
    Index          _sz;           // This is the official end.
    mutable std::atomic<Index>  _numTrueBits;
    static bool _enable_range_check;

protected:
    friend vespalib::nbostream &
    operator<<(vespalib::nbostream &out, const BitVector &bv);
    friend vespalib::nbostream &
    operator>>(vespalib::nbostream &in, AllocatedBitVector &bv);
};

vespalib::nbostream &
operator<<(vespalib::nbostream &out, const BitVector &bv);

vespalib::nbostream &
operator>>(vespalib::nbostream &in, AllocatedBitVector &bv);

template <typename T>
void BitVector::andNotWithT(T it) {
    while (it.hasNext()) {
        clearBit(it.next());
    }
    invalidateCachedCount();
}

} // namespace search
