// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/sortdata.h>
#include <vespa/searchlib/common/packets.h>
#include <vespa/document/base/globalid.h>

//-----------------------------------------------------------------------------

class FastS_hitresult;
class FastS_QueryResult;
class FastS_FNET_Search;
class FastS_FNET_SearchNode;

// T::HitType API

struct FastS_MergeHits_DummyHit
{
    document::GlobalId _emptyGid;
    uint32_t HT_GetDocID()                {   return 0; }
    const document::GlobalId & HT_GetGlobalID() { return _emptyGid; }
    search::HitRank HT_GetMetric()        {   return 0; }
    uint32_t HT_GetPartID()               {   return 0; }
    uint32_t getDistributionKey()             {   return 0; }
    void     HT_SetDocID(uint32_t val)    { (void) val; }
    void     HT_SetGlobalID(const document::GlobalId & val) { (void) val; }
    void     HT_SetMetric(search::HitRank val)   { (void) val; }
    void     HT_SetPartID(uint32_t val)   { (void) val; }
    void     setDistributionKey(uint32_t val) { (void) val; }
};

// T::NodeType API

struct FastS_MergeHits_DummyNode
{
    bool NT_InitMerge(uint32_t *numDocs, uint64_t *totalHits,
                      search::HitRank *maxRank, uint32_t *sortDataDocs)
    {
        (void) numDocs;
        (void) totalHits;
        (void) maxRank;
        (void) sortDataDocs;
        return false;
    }
    search::common::SortDataIterator *NT_GetSortDataIterator() { return NULL; }
    FastS_MergeHits_DummyHit *NT_GetHit()              { return NULL; }
    uint32_t                  NT_GetNumHitsUsed()      {    return 0; }
    uint32_t                  NT_GetNumHitsLeft()      {    return 0; }
    uint64_t                  NT_GetTotalHits()        {    return 0; }
    uint32_t                  NT_GetNumHits()          {    return 0; }
    void                      NT_NextHit()             {              }
};

// T::SearchType API

struct FastS_MergeHits_DummySearch
{
    FastS_MergeHits_DummyNode *ST_GetNode(size_t i)    { (void) i;    return NULL; }
    uint32_t           ST_GetNumNodes()                {       return 0; }
    bool               ST_IsEstimate()                 {   return false; }
    uint32_t           ST_GetEstParts()                {       return 0; }
    uint32_t           ST_GetEstPartCutoff()           {       return 0; }
    bool               ST_ShouldDropSortData()         {   return false; }
    bool               ST_ShouldLimitHitsPerNode()     {   return false; }
    void               ST_SetNumHits(uint32_t numHits) { (void) numHits; }
    uint32_t           ST_GetAlignedSearchOffset()     {       return 0; }
    uint32_t           ST_GetAlignedMaxHits()          {       return 0; }
    uint32_t           ST_GetAlignedHitCount()         {       return 0; }
    FastS_hitresult   *ST_GetAlignedHitBuf()           {    return NULL; }
    FastS_hitresult   *ST_GetAlignedHitBufEnd()        {    return NULL; }
    void               ST_AllocSortData(uint32_t len)  {     (void) len; }
    uint32_t          *ST_GetSortIndex()               {    return NULL; }
    char              *ST_GetSortData()                {    return NULL; }
    FastS_QueryResult *ST_GetQueryResult()             {    return NULL; }
};

// T (Merge Type) API

struct FastS_MergeHits_DummyMerge
{
    typedef FastS_MergeHits_DummyHit     HitType;
    typedef FastS_MergeHits_DummyNode    NodeType;
    typedef FastS_MergeHits_DummySearch  SearchType;
};

//-----------------------------------------------------------------------------

struct FastS_FNETMerge
{
    typedef search::fs4transport::FS4Packet_QUERYRESULTX::FS4_hit HitType;
    typedef FastS_FNET_SearchNode           NodeType;
    typedef FastS_FNET_Search               SearchType;
};

//-----------------------------------------------------------------------------

template <typename T>
class FastS_HitMerger
{
private:
    FastS_HitMerger(const FastS_HitMerger &);
    FastS_HitMerger& operator=(const FastS_HitMerger &);


    typedef typename T::NodeType   NODE;
    typedef typename T::SearchType SEARCH;

    // owning search object
    SEARCH               *_search;

    // sorting heap
    NODE                **_heap;
    uint32_t              _heapSize;
    uint32_t              _heapMax;

    // temporary array for merging sortdata
    search::common::SortData::Ref  *_sortRef;

    // limit hits per node effect variables
    NODE                 *_lastNode;
    bool                  _incomplete;
    bool                  _fuzzy;

public:
    FastS_HitMerger(SEARCH *search) : _search(search),
                                      _heap(NULL),
                                      _heapSize(0),
                                      _heapMax(0),
                                      _sortRef(NULL),
                                      _lastNode(NULL),
                                      _incomplete(false),
                                      _fuzzy(false)
    {}

    ~FastS_HitMerger()
    {
        delete [] _heap;
        delete [] _sortRef;
    }

    NODE **AllocHeap(uint32_t maxNodes);
    search::common::SortData::Ref *AllocSortRef(uint32_t size);
    void SetLastNode(NODE *lastNode) { _lastNode = lastNode; }

    SEARCH *GetSearch() const { return _search; }
    NODE **GetHeap() const { return _heap; }
    uint32_t GetHeapSize() const { return _heapSize; }
    uint32_t GetHeapMax() const { return _heapMax; }
    NODE *GetLastNode() const { return _lastNode; }
    bool WasIncomplete() const { return _incomplete; }
    bool WasFuzzy() const { return _fuzzy; }

    search::common::SortData::Ref *GetSortRef() const { return _sortRef; }

    void MergeHits();
};
