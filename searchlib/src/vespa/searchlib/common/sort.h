// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/optimized.h>
#include <vespa/vespalib/util/sort.h>
#include <functional>
#include <limits>
#include <algorithm>
#include <cstring>

namespace search {

bool radix_prepare(size_t n, size_t last[257], size_t ptr[256], size_t cnt[256]);

template<typename T>
void radix_sort_core(const size_t * last, T * a, size_t n, uint32_t * radixScratch, unsigned int shiftWidth) __attribute__ ((noinline));

template<typename T>
void radix_sort_core(const size_t * last, T * a, size_t n, uint32_t * radixScratch, unsigned int shiftWidth)
{
    T temp, swap;
    // Go through all permutation cycles until all
    // elements are moved or found to be already in place
    size_t ptr[256];
    size_t i, j, k;
    memcpy(ptr, last, sizeof(ptr));
    i = 0;
    size_t remain = n;

    while (remain > 0) {
        // Find first uncompleted class
        while (ptr[i] == last[i+1]) {
            i++;
        }

        // Grab first element to move
        j = ptr[i];
        uint32_t swapK = radixScratch[j];
        k = (swapK >> shiftWidth) & 0xFF;

        // Swap into correct class until cycle completed
        if (i != k) {
            swap = a[j];
            do {
                size_t t(ptr[k]);
                temp = a[t];
                uint32_t tempK(radixScratch[t]);
                radixScratch[t] = swapK;
                a[t] = swap;
                ptr[k]++;
                swapK = tempK;
                swap = temp;
                k = (tempK >> shiftWidth) & 0xFF;
                remain--;
            } while (i!=k);
            // Place last element in cycle
            a[j] = swap;
            radixScratch[j] = swapK;
        }
        ptr[k]++;
        remain--;
    }
}

template<typename T, typename GR>
unsigned int radix_fetch(T *a, size_t n, uint32_t * radixScratch, GR R) __attribute__ ((noinline));

template<typename T, typename GR>
unsigned int radix_fetch(T *a, size_t n, uint32_t * radixScratch, GR R)
{
    size_t i = 0;
    uint32_t usedBits = 0;
    if (n > 3) {
        for(; i < n - 3; i += 4) {
            radixScratch[i + 0] = R(a[i + 0]);
            radixScratch[i + 1] = R(a[i + 1]);
            radixScratch[i + 2] = R(a[i + 2]);
            radixScratch[i + 3] = R(a[i + 3]);
            usedBits |= radixScratch[i + 0];
            usedBits |= radixScratch[i + 1];
            usedBits |= radixScratch[i + 2];
            usedBits |= radixScratch[i + 3];
        }
    }
    for(; i < n; i++) {
        radixScratch[i] = R(a[i]);
        usedBits |= radixScratch[i];
    }
    if (usedBits != 0) {
        int msb = vespalib::Optimized::msbIdx(usedBits);
        return (msb+8) & ~0x7;
    }
    return 0;
}

template <typename T>
class AlwaysEof
{
public:
    bool operator () (const T &) const { return true; }
    static bool alwaysEofOnCheck() { return true; }
};

template<typename T, typename ER>
bool radix_eof(const T *a, size_t n, ER E) __attribute__ ((noinline));

template<typename T, typename ER>
bool radix_eof(const T *a, size_t n, ER E)
{
    size_t i = 0;
    bool eof(true);
    if (n > 3) {
        for(; eof && (i < n - 3); i += 4) {
            eof = E(a[i + 0]) &&
                  E(a[i + 1]) &&
                  E(a[i + 2]) &&
                  E(a[i + 3]);
        }
    }
    for(; eof && (i < n); i++) {
        eof = E(a[i]);
    }
    return eof;
}

/**
 * radix sort implementation.
 *
 * @param stackDepth recursion level reached; since radix_sort uses
 *                   lots of stack we try another algorithm if this
 *                   becomes too high.
 * @param a Pointer to the start of the array to sort
 * @param n number of data elements to sort
 * @param radixScratch scratch area for upto 32bits of sorting data
 * @param radixBits how many bits of sorting data radixScratch contains
 * @param insertSortLevel when to fall back to simple insertion sort
 **/
template<typename T, typename GR, typename GE, typename GRE>
void radix_sort(GR R, GE E, GRE EE, int stackDepth,
                T * a, size_t n,
                uint32_t *radixScratch,
                int radixBits,
                unsigned insertSortLevel=10,
                size_t topn=std::numeric_limits<size_t>::max())
{
    if (((stackDepth > 20) && (radixBits == 0)) || (n < insertSortLevel)) {
        // switch to simpler sort if few elements
        if (n > 1) {
            std::sort(a, a+n, E);
        }
        return;
    }

    size_t last[257];
    size_t cnt[256];
    int shiftWidth = radixBits - 8;
    for (bool allInOneBucket(true); allInOneBucket;) {
        while ( radixBits == 0 ) {
            // no data left in scratch buffer; fill up with upto 32 new bits
            radixBits = radix_fetch(a, n, radixScratch, R);
            if (radixBits == 0) {
                if (EE.alwaysEofOnCheck() || radix_eof(a, n, EE)) {
                    // everything has reached end-of-string terminating zero,
                    // so we are done sorting.
                    return;
                }
            }
        }

        shiftWidth = radixBits - 8;
        memset(cnt, 0, sizeof(cnt));
        size_t i = 0;
        if (n > 3) {
            for(; i < n - 3; i += 4) {
                cnt[(radixScratch[i + 0] >> shiftWidth) & 0xFF]++;
                cnt[(radixScratch[i + 1] >> shiftWidth) & 0xFF]++;
                cnt[(radixScratch[i + 2] >> shiftWidth) & 0xFF]++;
                cnt[(radixScratch[i + 3] >> shiftWidth) & 0xFF]++;
            }
        }
        for(; i < n; i++) {
            cnt[(radixScratch[i] >> shiftWidth) & 0xFF]++;
        }

        // Accumulate cnt positions
        allInOneBucket = false;
        last[0] = 0;
        for(i = 1; (i < 257) && !allInOneBucket; i++) {
            last[i] = last[i-1] + cnt[i-1];
            allInOneBucket = (cnt[i-1] == n);
        }

        radixBits -= 8;
    }

    radix_sort_core(last, a, n, radixScratch, shiftWidth);

    // Sort on next 8 bits of key
    for(size_t i(0), sum(0); (i<256) && (sum < topn); i++) {
        const size_t l(last[i]);
        const size_t c(cnt[i]);
        if (c) {
            if (c > insertSortLevel) {
                radix_sort(R, E, EE, stackDepth + 1, &a[l], c, &radixScratch[l], radixBits, insertSortLevel, topn-sum);
            } else {
                std::sort(&a[l], &a[l]+c, E);
            }
            sum += c;
        }
    }
}


template<typename GR, typename T, int SHIFT>
class ShiftBasedRadixSorterBase
{
protected:
    static void radix_fetch(GR R, size_t cnt[256], const T * a, size_t n) __attribute__((noinline));
    static void radix_sort_core(GR R, size_t ptr[256], size_t last[257], T * a, size_t n) __attribute__((noinline));
};

template<typename GR, typename T, int SHIFT>
void ShiftBasedRadixSorterBase<GR, T, SHIFT>::radix_fetch(GR R, size_t cnt[256], const T * a, size_t n)
{
    memset(cnt, 0, 256 * sizeof(cnt[0]));
    size_t p(0);
    if (n > 3) {
        for(; p < n - 3; p += 4) {
            cnt[(R(a[p]) >> SHIFT) & 0xFF]++;
            cnt[(R(a[p + 1]) >> SHIFT) & 0xFF]++;
            cnt[(R(a[p + 2]) >> SHIFT) & 0xFF]++;
            cnt[(R(a[p + 3]) >> SHIFT) & 0xFF]++;
        }
    }
    for(; p < n; p++) {
        cnt[(R(a[p]) >> SHIFT) & 0xFF]++;
    }
}


template<typename GR, typename T, int SHIFT>
void ShiftBasedRadixSorterBase<GR, T, SHIFT>::radix_sort_core(GR R, size_t ptr[256], size_t last[257], T * a, size_t n)
{
    // Go through all permutation cycles until all
    // elements are moved or found to be already in place
    size_t i(0), remain(n);
    size_t j, k;
    T temp, swap;

    while(remain>0) {
        // Find first uncompleted class
        while(ptr[i]==last[i+1]) {
            i++;
        }

        // Grab first element to move
        j = ptr[i];
        k = (R(a[j]) >> SHIFT) & 0xFF;

        // Swap into correct class until cycle completed
        if (i!=k) {
            swap = a[j];
            do {
                temp = a[ptr[k]];
                a[ptr[k]++] = swap;
                k = (R(swap=temp) >> SHIFT) & 0xFF;
                remain--;
            } while (i!=k);
            // Place last element in cycle
            a[j] = swap;
        }
        ptr[k]++;
        remain--;
    }
}

/**
 * @param T the type of the object being sorted
 * @param GR the functor used to fetch the number used for radix sorting. It must enure same sorting as GE.
 * @param GE the functor used for testing if one object is orderers ahead of another.
 * @param SHIFT is the number of significant bits in the radix - 8. Must a multiple of 8.
 * @param continueAfterRadixEnds indicates if the radix only represents a prefix of the objects. If it is true we
 *        will continue using std::sort to order objects that have equal radix representation.
 */ 
template<typename T, typename GR, typename GE, int SHIFT, bool continueAfterRadixEnds=false>
class ShiftBasedRadixSorter : private ShiftBasedRadixSorterBase<GR, T, SHIFT>
{
public:
    static size_t radix_sort(GR R, GE E, T * a, size_t n, unsigned int insertSortLevel=10, size_t topn=std::numeric_limits<size_t>::max());
    static size_t radix_sort_internal(GR R, GE E, T * a, size_t n, unsigned int insertSortLevel, size_t topn);
private:
    using Base = ShiftBasedRadixSorterBase<GR, T, SHIFT>;
};

template<typename T, typename GR, typename GE, int SHIFT, bool continueAfterRadixEnds>
size_t ShiftBasedRadixSorter<T, GR, GE, SHIFT, continueAfterRadixEnds>::radix_sort_internal(GR R, GE E, T * a, size_t n, unsigned int insertSortLevel, size_t topn)
{
    size_t last[257], ptr[256], cnt[256];
    size_t sum(n);

    Base::radix_fetch(R, cnt, a, n);

    bool sorted = radix_prepare(n, last, ptr, cnt);

    if (!sorted) {
        Base::radix_sort_core(R, ptr, last, a, n);
    } else {
        return ShiftBasedRadixSorter<T, GR, GE, SHIFT - 8, continueAfterRadixEnds>::radix_sort_internal(R, E, a, n, insertSortLevel, topn);
    }

    if (SHIFT>0 || continueAfterRadixEnds) {
        // Sort on next key
        sum = 0;
        for(unsigned i(0); (i<256) && (sum < topn); i++) {
            const size_t c(cnt[i]);
            const size_t l(last[i]);
            if (c) {
                if (c>insertSortLevel) {
                    sum += ShiftBasedRadixSorter<T, GR, GE, SHIFT - 8, continueAfterRadixEnds>::radix_sort_internal(R, E, &a[l], c, insertSortLevel, topn-sum);
                } else {
                    std::sort(a+l, a+l+c, E);
                    sum += c;
                }
            }
        }
    }
    return sum;
}


template<typename T, typename GR, typename GE, int SHIFT, bool continueAfterRadixEnds>
size_t ShiftBasedRadixSorter<T, GR, GE, SHIFT, continueAfterRadixEnds>::radix_sort(GR R, GE E, T * a, size_t n, unsigned int insertSortLevel, size_t topn)
{
    if (n > insertSortLevel) {
        return radix_sort_internal(R, E, a, n, insertSortLevel, topn);
    } else if (n > 1) {
        std::sort(a, a + n, E);
    }
    return n;
}

template<typename A, typename B, typename C>
class ShiftBasedRadixSorter<A, B, C, -8, false> {
public:
    static size_t radix_sort_internal(B, C, A *, size_t, unsigned int, size_t) {
        return 0;
    }
};

template<typename A, typename B, typename C>
class ShiftBasedRadixSorter<A, B, C, -8, true> {
public:
    static size_t radix_sort_internal(B, C E, A * v, size_t sz, unsigned int, size_t) {
        std::sort(v, v + sz, E);
        return sz;
    }
};

template<typename T, bool asc=true>
class NumericRadixSorter
{
public:
    using C = vespalib::convertForSort<T, asc>;
    class RadixSortable {
    public:
        typename C::UIntType operator () (typename C::InputType v) const { return C::convert(v); }
    };
    void operator() (T * start, size_t sz, size_t topn = std::numeric_limits<size_t>::max()) const {
        if (sz > 16) {
            ShiftBasedRadixSorter<typename C::InputType, RadixSortable, typename C::Compare, 8*(sizeof(typename C::UIntType) -1)>::radix_sort_internal(RadixSortable(), typename C::Compare(), start, sz, 16, topn);
        } else {
            std::sort(start, start + sz, typename C::Compare());
        }
    }
};

template<typename GR, typename T, int IDX>
void radix_fetch2(GR R, size_t cnt[256], const T * a, size_t n) __attribute__ ((noinline));

template<typename GR, typename T, int IDX>
void radix_fetch2(GR R, size_t cnt[256], const T * a, size_t n)
{
    memset(cnt, 0, 256*sizeof(cnt[0]));
    size_t p(0);
    if (n > 3) {
        for(; p < n - 3; p += 4) {
            cnt[R(a[p + 0], IDX)]++;
            cnt[R(a[p + 1], IDX)]++;
            cnt[R(a[p + 2], IDX)]++;
            cnt[R(a[p + 3], IDX)]++;
        }
    }
    for(; p < n; p++) {
        cnt[R(a[p], IDX)]++;
    }
}

template<typename T, typename GR, typename GE, int LEN, int POS>
void radix_sort_internal(GR R, GE E, T * a, size_t n, unsigned int insertSortLevel, size_t topn)
{
    size_t last[257], ptr[256], cnt[256];

    radix_fetch2<GR, T, LEN-POS>(R, cnt, a, n);

    bool sorted = radix_prepare(n, last, ptr, cnt);

    if (!sorted) {
        // Go through all permutation cycles until all
        // elements are moved or found to be already in place
        size_t i(0), remain(n);
        size_t j, k;
        T temp, swap;

        while(remain>0) {
            // Find first uncompleted class
            while(ptr[i]==last[i+1]) {
                i++;
            }

            // Grab first element to move
            j = ptr[i];
            k = R(a[j], LEN-POS);

            // Swap into correct class until cycle completed
            if (i!=k) {
                swap = a[j];
                do {
                    temp = a[ptr[k]];
                    a[ptr[k]++] = swap;
                    k = R(swap=temp, LEN-POS);
                    remain--;
                } while (i!=k);
                // Place last element in cycle
                a[j] = swap;
            }
            ptr[k]++;
            remain--;
        }
    } else {
        radix_sort_internal<T, GR, GE, LEN, POS - 1>(R, E, a, n, insertSortLevel, topn);
        return;
    }

    if (LEN>0) {
        // Sort on next key
        for(size_t i(0), sum(0); (i<256) && (sum < topn); i++) {
            const size_t c(cnt[i]);
            const size_t l(last[i]);
            if (c) {
                if (c>insertSortLevel) {
                    radix_sort_internal<T, GR, GE, LEN, POS - 1>(R, E, &a[l], c, insertSortLevel, topn-sum);
                } else {
                    std::sort(a+l, a+l+c, E);
                }
                sum += c;
            }
        }
    }
}


template<typename T, typename GR, typename GE, int LEN, int POS>
void radix_sort(GR R, GE E, T * a, size_t n, unsigned int insertSortLevel=10, size_t topn=std::numeric_limits<size_t>::max())
{
    if (n > insertSortLevel) {
        radix_sort_internal<T, GR, GE, LEN, POS>(R, E, a, n, insertSortLevel, topn);
    } else if (n > 1) {
        std::sort(a, a + n, E);
    }
}


template<typename T, typename GR, int SHIFT>
void radix_stable_core(GR R, size_t ptr[256], const T * a, T * b, size_t n) __attribute__ ((noinline));

template<typename T, typename GR, int SHIFT>
void radix_stable_core(GR R, size_t ptr[256], const T * a, T * b, size_t n)
{
    size_t k;
    for (size_t i(0); i < n; i++) {
        k = (R(a[i]) >> SHIFT) & 0xFF;
        b[ptr[k]] = a[i];
        ptr[k]++;
    }
}

template<typename T, typename GR, typename GE, int SHIFT>
T * radix_stable_sort_internal(GR R, GE E, T * a, T * b, size_t n, unsigned int insertSortLevel=10)
{
    size_t last[257], ptr[256], cnt[256];

    radix_fetch<GR, T, SHIFT>(R, cnt, a, n);

    bool sorted = radix_prepare(n, last, ptr, cnt);

    if (!sorted) {
        radix_stable_core<T, R, SHIFT>(R, ptr, a, b, n);
    } else {
        return radix_stable_sort_internal<T, GR, GE, SHIFT - 8>(R, E, a, b, n, insertSortLevel);
    }

    if (SHIFT>0) {
        // Sort on next key
        for(unsigned i(0); i<256 ; i++) {
            const size_t c(cnt[i]);
            const size_t l(last[i]);
            if (c>insertSortLevel) {
                const T * r = radix_stable_sort_internal<T, GR, GE, SHIFT - 8>(R, E, &b[l], &a[l], c, insertSortLevel);
                if (r != &b[l]) {
                    memcpy(&b[l], &a[l], c*sizeof(*r));
                }
            } else {
                if (c>1) {
                    std::stable_sort(b+l, b+l+c, E);
                }
            }
        }
    }
    return b;
}

template<typename T, typename GR, typename GE, int SHIFT>
T* radix_stable_sort(GR R, GE E, T * a, T * b, size_t n, unsigned int insertSortLevel=10)
{
    if (n > insertSortLevel) {
        return radix_stable_sort_internal<T, GR, GE, SHIFT>(R, E, a, b, n, insertSortLevel);
    } else if (n > 1) {
        std::stable_sort(a, a + n, E);
    }
    return a;
}

}

