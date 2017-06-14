// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mergehits.h"
#include "querycacheutil.h"
#include "fnet_dataset.h"
#include "fnet_search.h"
#include <vespa/searchcore/util/stlishheap.h>

#include <vespa/log/log.h>
LOG_SETUP(".fdispatch.mergehits");

using search::common::SortData;
using search::common::SortDataIterator;

//-----------------------------------------------------------------------------

template <bool SORTDATA, bool DROP>
struct FastS_MergeFeatures
{
    static bool UseSortData()  { return SORTDATA; }
    static bool DropSortData() {     return DROP; }
};


template <typename T, typename F>
bool
FastS_MergeCompare(typename T::NodeType *a,
                   typename T::NodeType *b)
{
    bool prefer_b = (b->NT_GetNumHitsUsed() < a->NT_GetNumHitsUsed());
    if (F::UseSortData()) {
        return b->NT_GetSortDataIterator()->Before(a->NT_GetSortDataIterator(), prefer_b);
    } else {
        search::HitRank rank_a = a->NT_GetHit()->HT_GetMetric();
        search::HitRank rank_b = b->NT_GetHit()->HT_GetMetric();
        return ((rank_b > rank_a) || ((rank_b == rank_a) && prefer_b));
    }
}


template <typename T>
inline void
FastS_MergeCopySortData(typename T::NodeType *node,
                        SortData::Ref *dst,
                        uint32_t &sortDataLen)
{
    if (dst == NULL)
        return;

    SortDataIterator *src = node->NT_GetSortDataIterator();
    dst->_buf = src->GetBuf();
    dst->_len = src->GetLen();
    sortDataLen += dst->_len;
}


template <typename T>
inline void
FastS_MergeCopyHit(typename T::HitType *src,
                   FastS_hitresult *dst)
{
    dst->HT_SetGlobalID(src->HT_GetGlobalID());
    dst->HT_SetMetric(src->HT_GetMetric());
    dst->HT_SetPartID(src->HT_GetPartID());
    dst->setDistributionKey(src->getDistributionKey());
}



template <typename T, typename F>
void
FastS_InternalMergeHits(FastS_HitMerger<T> *merger)
{
    typename T::SearchType *search   = merger->GetSearch();
    typename T::NodeType  **heap     = merger->GetHeap();
    uint32_t                heapSize = merger->GetHeapSize();
    typename T::NodeType   *node     = NULL;
    FastS_hitresult *beg = search->ST_GetAlignedHitBuf();
    FastS_hitresult *end = search->ST_GetAlignedHitBufEnd();
    FastS_hitresult *pt  = beg;

    // multi-level sorting related variables
    SortData::Ref *sortRef = NULL;
    SortData::Ref *sortItr = NULL;
    uint32_t         sortDataLen = 0;

    if (F::UseSortData() && !F::DropSortData()) {
        sortRef = merger->AllocSortRef(end - beg);
        sortItr = sortRef;
    }

    FastS_make_heap(heap, heapSize, FastS_MergeCompare<T, F>);

    while (pt < end) {
        node = *heap;
        FastS_assert(heapSize > 0);
        if (F::UseSortData()) {
            if (!F::DropSortData()) {
                FastS_MergeCopySortData<T>(node, sortItr++, sortDataLen);
            }
            node->NT_GetSortDataIterator()->Next();
        }
        FastS_MergeCopyHit<T>(node->NT_GetHit(), pt++);
        node->NT_NextHit();
        if (node->NT_GetNumHitsLeft() > 0) {
            FastS_pop_push_heap(heap, heapSize, node, FastS_MergeCompare<T, F>);
        } else {
            FastS_pop_heap(heap, heapSize--, FastS_MergeCompare<T, F>);
        }
    }
    merger->SetLastNode(node); // source of last hit
    if (F::UseSortData()) {
        FastS_assert(F::DropSortData() || sortItr == sortRef + (end - beg));
    }

    // generate merged sort data
    if (F::UseSortData() && sortDataLen > 0) {

        FastS_assert(!F::DropSortData());
        search->ST_AllocSortData(sortDataLen);

        uint32_t  offset   = 0;
        uint32_t *sortIdx  = search->ST_GetSortIndex();
        char     *sortData = search->ST_GetSortData();

        sortItr = sortRef;
        for (uint32_t residue = (end - beg); residue > 0; residue--) {
            *sortIdx++ = offset;
            memcpy(sortData + offset, sortItr->_buf, sortItr->_len);
            offset += sortItr->_len;
            sortItr++;
        }
        *sortIdx = offset;
        FastS_assert(sortItr == sortRef + (end - beg));
        FastS_assert(offset == sortDataLen);
    }
}

//-----------------------------------------------------------------------------

template <typename T>
typename FastS_HitMerger<T>::NODE **
FastS_HitMerger<T>::AllocHeap(uint32_t maxNodes)
{
    FastS_assert(_heap == NULL);
    _heap = new NODE*[maxNodes];
    _heapSize = 0;
    _heapMax = maxNodes;
    return _heap;
}


template <typename T>
SortData::Ref *
FastS_HitMerger<T>::AllocSortRef(uint32_t size)
{
    FastS_assert(_sortRef == NULL);
    _sortRef = new SortData::Ref[size];
    return _sortRef;
}


template <typename T>
void
FastS_HitMerger<T>::MergeHits()
{
    uint32_t numNodes     = _search->ST_GetNumNodes();
    bool     dropSortData = _search->ST_ShouldDropSortData();
    bool     useSortData  = false;
    uint32_t numDocs      = 0;
    uint64_t totalHits    = 0;
    search::HitRank maxRank =
        std::numeric_limits<search::HitRank>::is_integer ?
        std::numeric_limits<search::HitRank>::min() :
        - std::numeric_limits<search::HitRank>::max();
    uint32_t sortDataDocs = 0;

    FastS_QueryResult *result = _search->ST_GetQueryResult();

    // just set totalHitCount for estimates
    if (_search->ST_IsEstimate()) {
        for (uint32_t i = 0; i < numNodes; i++) {
            _search->ST_GetNode(i)->NT_InitMerge(&numDocs, &totalHits,
                                  &maxRank, &sortDataDocs);
        }
        result->_totalHitCount = (_search->ST_GetEstParts() == 0) ? 0
                                 : (uint64_t) (((double)totalHits
                                                * (double)_search->ST_GetEstPartCutoff())
                                               / (double)_search->ST_GetEstParts());
        return;
    }

    // prepare nodes for merging
    NODE **heap = AllocHeap(numNodes);
    for (uint32_t i = 0; i < numNodes; i++) {
        if (_search->ST_GetNode(i)->NT_InitMerge(&numDocs, &totalHits,
                                  &maxRank, &sortDataDocs))
        {
            heap[_heapSize++] = _search->ST_GetNode(i);
        }
    }

    // check if we should use sort data for sorting
    if (sortDataDocs > 0) {
        if (sortDataDocs == numDocs) {
            useSortData = true;
        } else {
            LOG(warning, "Some results are missing sort data, sorting by rank instead");
        }
    }

    // set some result variables
    result->_totalHitCount = totalHits;
    result->_maxRank = maxRank;

    // allocate some needed structures
    _search->ST_SetNumHits(numDocs); // NB: allocs result buffer

    // do actual merging by invoking templated function
    if (useSortData) {
        if (dropSortData) {
            FastS_InternalMergeHits
                <T, FastS_MergeFeatures<true, true> >(this);
        } else {
            FastS_InternalMergeHits
                <T, FastS_MergeFeatures<true, false> >(this);
        }
    } else {
        FastS_InternalMergeHits
            <T, FastS_MergeFeatures<false, false> >(this);
    }

    // detect incomplete/fuzzy results
    if (_search->ST_ShouldLimitHitsPerNode()) {
        if (_search->ST_GetAlignedHitCount() < _search->ST_GetAlignedMaxHits() &&
            result->_totalHitCount > (_search->ST_GetAlignedSearchOffset() +
                                      _search->ST_GetAlignedHitCount()))
        {
            _incomplete = true;
        }

        NODE *lastNode = GetLastNode();
        for (size_t i(0), m(_search->ST_GetNumNodes()); i < m; i++) {
            NODE *node(_search->ST_GetNode(i));
            if (node == lastNode ||
                node->NT_GetTotalHits() == 0)
                continue;
            if (node->NT_GetNumHitsLeft() == 0 &&
                node->NT_GetTotalHits() > (_search->ST_GetAlignedSearchOffset() +
                                           node->NT_GetNumHits()))
            {
                _fuzzy = true;
                break;
            }
        }
    }
}

//-----------------------------------------------------------------------------

template class FastS_HitMerger<FastS_MergeHits_DummyMerge>; // for API check
template class FastS_HitMerger<FastS_FNETMerge>;

//-----------------------------------------------------------------------------
