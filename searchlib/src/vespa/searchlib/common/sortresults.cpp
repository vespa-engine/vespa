// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sortresults.h"
#include <vespa/searchlib/util/sort.h>
#include <vespa/searchlib/common/sort.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/document/base/globalid.h>
#include <vespa/vespalib/util/array.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".search.attribute.sortresults");

using search::RankedHit;
using search::common::SortSpec;
using search::common::SortInfo;
using search::attribute::IAttributeContext;
using search::attribute::IAttributeVector;
using vespalib::alloc::Alloc;
using namespace vespalib;

namespace {

constexpr size_t MMAP_LIMIT = 0x2000000;

template<typename T>
class RadixHelper
{
public:
    typedef convertForSort<T, true> C;
    inline typename C::UIntType
    operator()(typename C::InputType v) const {
        return C::convert(v);
    }
};

} // namespace <unnamed>


inline void
FastS_insertion_sort(RankedHit a[], uint32_t n)
{
    uint32_t i, j;
    RankedHit swap;
    typedef RadixHelper<search::HitRank> RT;
    RT R;

    for (i=1; i<n ; i++) {
        swap = a[i];
        j = i;
        while (R(swap._rankValue) > R(a[j-1]._rankValue)) {
            a[j] = a[j-1];
            if (!(--j)) break;;
        }
        a[j] = swap;
    }
}


template<int SHIFT>
void
FastS_radixsort(RankedHit a[], uint32_t n, uint32_t ntop)
{
    uint32_t last[256], ptr[256], cnt[256];
    uint32_t sorted, remain;
    uint32_t i, j, k;
    RankedHit temp, swap;
    typedef RadixHelper<search::HitRank> RT;
    RT R;

    memset(cnt, 0, 256*sizeof(uint32_t));
    // Count occurrences [NB: will fail with n < 3]
    for(i = 0; i < n - 3; i += 4) {
        cnt[(R(a[i]._rankValue) >> SHIFT) & 0xFF]++;
        cnt[(R(a[i + 1]._rankValue) >> SHIFT) & 0xFF]++;
        cnt[(R(a[i + 2]._rankValue) >> SHIFT) & 0xFF]++;
        cnt[(R(a[i + 3]._rankValue) >> SHIFT) & 0xFF]++;
    }
    for(; i < n; i++)
        cnt[(R(a[i]._rankValue) >> SHIFT) & 0xFF]++;

    // Accumulate cnt positions
    sorted = (cnt[0]==n);
    ptr[0] = n-cnt[0];
    last[0] = n;
    for(i=1; i<256; i++) {
        ptr[i] = (last[i]=ptr[i-1]) - cnt[i];
        sorted |= (cnt[i]==n);
    }

    if (!sorted) {
        // Go through all permutation cycles until all
        // elements are moved or found to be already in place
        i = 255;
        remain = n;

        while(remain>0) {
            // Find first uncompleted class
            while(ptr[i]==last[i]) {
                i--;
            }

            // Stop if top candidates in place
            if (last[i]-cnt[i]>=ntop) break;

            // Grab first element to move
            j = ptr[i];
            swap = a[j];
            k = (R(swap._rankValue) >> SHIFT) & 0xFF;

            // Swap into correct class until cycle completed
            if (i!=k) {
                do {
                    temp = a[ptr[k]];
                    a[ptr[k]++] = swap;
                    k = (R((swap = temp)._rankValue) >> SHIFT) & 0xFF;
                    remain--;
                } while (i!=k);
                // Place last element in cycle
                a[j] = swap;
            }
            ptr[k]++;
            remain--;
        }
    } else {
        FastS_radixsort<SHIFT - 8>(a, n, ntop);
        return;
    }

    if (SHIFT>0) {
        // Sort on next key
        for(i=0; i<256 ; i++) {
            if ((last[i]-cnt[i])<ntop) {
                if (cnt[i]>INSERT_SORT_LEVEL) {
                    if (last[i]<ntop) {
                        FastS_radixsort<SHIFT - 8>(&a[last[i]-cnt[i]], cnt[i],
                                cnt[i]);
                    } else {
                        FastS_radixsort<SHIFT - 8>(&a[last[i]-cnt[i]], cnt[i],
                                cnt[i]+ntop-last[i]);
                    }
                } else if (cnt[i]>1) {
                        FastS_insertion_sort(&a[last[i]-cnt[i]], cnt[i]);
                }
            }
        }
    }
}
template<>
void
FastS_radixsort<-8>(RankedHit *, uint32_t, uint32_t) {}

void
FastS_SortResults(RankedHit a[], uint32_t n, uint32_t ntop)
{
    if (n > INSERT_SORT_LEVEL) {
        FastS_radixsort<sizeof(search::HitRank)*8 - 8>(a, n, ntop);
    } else {
        FastS_insertion_sort(a, n);
    }
}

//-----------------------------------------------------------------------------

FastS_DefaultResultSorter FastS_DefaultResultSorter::__instance;

//-----------------------------------------------------------------------------

FastS_DocIdResultSorter FastS_DocIdResultSorter::__instance;

//-----------------------------------------------------------------------------

bool
FastS_SortSpec::Add(IAttributeContext & vecMan, const SortInfo & sInfo)
{
    if (sInfo._field.empty())
        return false;

    uint32_t          type   = ASC_VECTOR;
    const IAttributeVector * vector(NULL);

    if ((sInfo._field.size() == 6) && (sInfo._field == "[rank]")) {
        type = (sInfo._ascending) ? ASC_RANK : DESC_RANK;
    } else if ((sInfo._field.size() == 7) && (sInfo._field == "[docid]")) {
        type = (sInfo._ascending) ? ASC_DOCID : DESC_DOCID;
    } else {
        type = (sInfo._ascending) ? ASC_VECTOR : DESC_VECTOR;
        vector = vecMan.getAttribute(sInfo._field);
        if ( !vector || vector->hasMultiValue()) {
            const char * err = "OK";
            if ( !vector ) {
                err = "not valid";
            } else  if ( vector->hasMultiValue()) {
                err = "multivalued";
            }
            LOG(warning, "Attribute vector '%s' is %s. Skipped in sorting", sInfo._field.c_str(), err);
            return false;
        }
    }

    LOG(spam, "SortSpec: adding vector (%s)'%s'",
        (sInfo._ascending) ? "+" : "-", sInfo._field.c_str());

    _vectors.push_back(VectorRef(type, vector, sInfo._converter.get()));

    return true;
}

uint8_t *
FastS_SortSpec::realloc(uint32_t n, size_t & variableWidth, uint32_t & available, uint32_t & dataSize, uint8_t *mySortData)
{
    // realloc
    variableWidth *= 2;
    available += variableWidth * n;
    dataSize += variableWidth * n;
    uint32_t byteUsed = mySortData - &_binarySortData[0];
    _binarySortData.resize(dataSize);
    return &_binarySortData[0] + byteUsed;
}

void
FastS_SortSpec::initSortData(const RankedHit *hits, uint32_t n)
{
    freeSortData();
    size_t fixedWidth = 0;
    size_t variableWidth = 0;
    for (auto iter = _vectors.begin(); iter != _vectors.end(); ++iter) {
        if (iter->_type >= ASC_DOCID) { // doc id
            fixedWidth += sizeof(uint32_t) + sizeof(uint16_t);
        }else if (iter->_type >= ASC_RANK) { // rank value
            fixedWidth += sizeof(search::HitRank);
        } else {
            size_t numBytes = iter->_vector->getFixedWidth();
            if (numBytes == 0) { // string
                variableWidth += 11;
            } else if (!iter->_vector->hasMultiValue()) {
                fixedWidth += numBytes;
            }
        }
    }
    uint32_t dataSize = (fixedWidth + variableWidth) * n;
    uint32_t available = dataSize;
    _binarySortData.resize(dataSize);
    uint8_t *mySortData = &_binarySortData[0];

    _sortDataArray.resize(n);

    document::GlobalId gid;
    for (uint32_t i(0), idx(0); (i < n) && !_doom.doom(); ++i) {
        uint32_t len = 0;
        for (auto iter = _vectors.begin(); iter != _vectors.end(); ++iter) {
            int written(0);
            if (available < std::max(sizeof(hits->_docId) + sizeof(_partitionId), sizeof(hits->_rankValue))) {
                mySortData = realloc(n, variableWidth, available, dataSize, mySortData);
            }
            do {
                switch (iter->_type) {
                case ASC_DOCID:
                    serializeForSort<convertForSort<uint32_t, true> >(hits[i].getDocId(), mySortData);
                    serializeForSort<convertForSort<uint16_t, true> >(_partitionId, mySortData + sizeof(hits->_docId));
                    written = sizeof(hits->_docId) + sizeof(_partitionId);
                    break;
                case DESC_DOCID:
                    serializeForSort<convertForSort<uint32_t, false> >(hits[i].getDocId(), mySortData);
                    serializeForSort<convertForSort<uint16_t, false> >(_partitionId, mySortData + sizeof(hits->_docId));
                    written = sizeof(hits->_docId) + sizeof(_partitionId);
                    break;
                case ASC_RANK:
                    serializeForSort<convertForSort<search::HitRank, true> >(hits[i]._rankValue, mySortData);
                    written = sizeof(hits->_rankValue);
                    break;
                case DESC_RANK:
                    serializeForSort<convertForSort<search::HitRank, false> >(hits[i]._rankValue, mySortData);
                    written = sizeof(hits->_rankValue);
                    break;
                case ASC_VECTOR:
                    written = iter->_vector->serializeForAscendingSort(hits[i].getDocId(), mySortData, available, iter->_converter);
                    break;
                case DESC_VECTOR:
                    written = iter->_vector->serializeForDescendingSort(hits[i].getDocId(), mySortData, available, iter->_converter);
                    break;
                }
                if (written == -1) {
                    mySortData = realloc(n, variableWidth, available, dataSize, mySortData);
                }
            } while(written == -1);
            available -= written;
            mySortData += written;
            len += written;
        }
        SortData & sd = _sortDataArray[i];
        sd._docId = hits[i]._docId;
        sd._rankValue = hits[i]._rankValue;
        sd._idx = idx;
        sd._len = len;
        sd._pos = 0;
        idx += len;
    }
}


FastS_SortSpec::FastS_SortSpec(uint32_t partitionId, const Doom & doom, const ConverterFactory & ucaFactory, int method) :
    _partitionId(partitionId),
    _doom(doom),
    _ucaFactory(ucaFactory),
    _method(method),
    _sortSpec(),
    _vectors()
{ }


FastS_SortSpec::~FastS_SortSpec()
{
    freeSortData();
}


bool
FastS_SortSpec::Init(const string & sortStr, IAttributeContext & vecMan)
{
    LOG(spam, "sortStr = %s", sortStr.c_str());
    bool retval(true);
    try {
        _sortSpec = SortSpec(sortStr, _ucaFactory);
        for (SortSpec::const_iterator it(_sortSpec.begin()), mt(_sortSpec.end()); retval && (it < mt); it++) {
            retval = Add(vecMan, *it);
        }
    } catch (const std::exception & e) {
        LOG(warning, "Failed parsing sortspec: %s", sortStr.c_str());
        return retval;
    }

    return retval;
}


uint32_t
FastS_SortSpec::getSortDataSize(uint32_t offset, uint32_t n)
{
    uint32_t size = 0;
    for (uint32_t i = offset; i < (offset + n); ++i) {
        size += _sortDataArray[i]._len;
    }
    return size;
}

void
FastS_SortSpec::copySortData(uint32_t offset, uint32_t n,
                             uint32_t *idx, char *buf)
{
    const uint8_t * sortData = &_binarySortData[0];
    uint32_t totalLen = 0;
    for (uint32_t i = offset; i < (offset + n); ++i, ++idx) {
        const uint8_t * src = sortData + _sortDataArray[i]._idx;
        uint32_t len = _sortDataArray[i]._len;
        memcpy(buf, src, len);
        buf += len;
        *idx = totalLen;
        totalLen += len;
    }
    *idx = totalLen; // end of data index entry
}

void
FastS_SortSpec::freeSortData()
{
    {
        BinarySortData tmp;
        _binarySortData.swap(tmp);
    }
    {
        SortDataArray tmp;
        _sortDataArray.swap(tmp);
    }
}

bool
FastS_SortSpec::hasSortData() const
{
    return ! _binarySortData.empty() && ! _sortDataArray.empty();
}

void
FastS_SortSpec::initWithoutSorting(const RankedHit * hits, uint32_t hitCnt)
{
    initSortData(hits, hitCnt);
}

inline int
FastS_SortSpec::Compare(const FastS_SortSpec *self, const SortData &a,
                        const SortData &b)
{
    const uint8_t * ref = &(self->_binarySortData[0]);
    uint32_t len = a._len < b._len ? a._len : b._len;
    int retval = memcmp(ref + a._idx,
                        ref + b._idx, len);
    if (retval < 0) {
        return -1;
    } else if (retval > 0) {
        return 1;
    }
    return 0;
}

template <typename T, typename Compare>
inline T *
FastS_median3(T *a, T *b, T *c, Compare *compobj)
{
    return Compare::Compare(compobj, *a, *b) < 0 ?
        (Compare::Compare(compobj, *b, *c) < 0 ? b : Compare::Compare(compobj,
                *a, *c) < 0 ? c : a) :
        (Compare::Compare(compobj, *b, *c) > 0 ? b : Compare::Compare(compobj,
                *a, *c) > 0 ? c : a);
}


template <typename T, typename Compare>
void
FastS_insertion_sort(T a[], uint32_t n, Compare *compobj)
{
    uint32_t i, j;
    T swap;

    for (i=1; i<n ; i++) {
        swap = a[i];
        j = i;
        while (Compare::Compare(compobj, swap, a[j-1]) < 0) {
            a[j] = a[j-1];
            if (!(--j)) break;;
        }
        a[j] = swap;
    }
}

class StdSortDataCompare : public std::binary_function<FastS_SortSpec::SortData, FastS_SortSpec::SortData, bool>
{
public:
    StdSortDataCompare(const uint8_t * s) : _sortSpec(s) { }
    bool operator() (const FastS_SortSpec::SortData & x, const FastS_SortSpec::SortData & y) const {
        return cmp(x, y) < 0;
    }
    int cmp(const FastS_SortSpec::SortData & a, const FastS_SortSpec::SortData & b) const {
        uint32_t len = std::min(a._len, b._len);
        int retval = memcmp(_sortSpec + a._idx, _sortSpec + b._idx, len);
        return retval ? retval : a._len - b._len;
    }
private:
    const uint8_t * _sortSpec;
};

class SortDataRadix
{
public:
    SortDataRadix(const uint8_t * s) : _data(s) { }
    uint32_t operator () (FastS_SortSpec::SortData & a) const {
        uint32_t r(0);
        uint32_t left(a._len - a._pos);
        switch (left) {
        default:
        case 4:
            r |= _data[a._idx + a._pos + 3] << 0;
            [[fallthrough]];
        case 3:
            r |= _data[a._idx + a._pos + 2] << 8;
            [[fallthrough]];
        case 2:
            r |= _data[a._idx + a._pos + 1] << 16;
            [[fallthrough]];
        case 1:
            r |= _data[a._idx + a._pos + 0] << 24;
            [[fallthrough]];
        case 0:;
        }
        a._pos += std::min(4u, left);
        return r;
    }
private:
    const uint8_t * _data;
};

class SortDataEof
{
public:
    bool operator () (const FastS_SortSpec::SortData & a) const { return a._pos >= a._len; }
    static bool alwaysEofOnCheck() { return false; }
};


void
FastS_SortSpec::sortResults(RankedHit a[], uint32_t n, uint32_t topn)
{
    initSortData(a, n);
    SortData * sortData = &_sortDataArray[0];
    if (_method == 0) {
        search::qsort<7, 40, SortData, FastS_SortSpec>(sortData, n, this);
    } else if (_method == 1) {
        std::sort(sortData, sortData + n, StdSortDataCompare(&_binarySortData[0]));
    } else {
        Array<uint32_t> radixScratchPad(n, Alloc::alloc(0, MMAP_LIMIT));
        search::radix_sort(SortDataRadix(&_binarySortData[0]), StdSortDataCompare(&_binarySortData[0]), SortDataEof(), 1, sortData, n, &radixScratchPad[0], 0, 96, topn);
    }
    for (uint32_t i(0), m(_sortDataArray.size()); i < m; ++i) {
        a[i]._rankValue = _sortDataArray[i]._rankValue;
        a[i]._docId = _sortDataArray[i]._docId;
    }
}
