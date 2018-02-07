// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/fdispatch/search/query.h>
#include <vespa/searchlib/common/transport.h>

#include <vespa/searchcore/fdispatch/common/search.h>
#include <vespa/searchcore/util/log.h>

class FastS_DataSetCollection;

class FastS_QueryCacheUtil
{
private:
    FastS_QueryCacheUtil(const FastS_QueryCacheUtil &);
    FastS_QueryCacheUtil& operator=(const FastS_QueryCacheUtil &);

    double _startTime;      // For the query

    uint32_t _userMaxHits;   // Max hits spec.d by user; NB: see _systemMaxHits
    uint32_t _alignedMaxHits;    // Max hits      (forwarded to engine)
    uint32_t _alignedSearchOffset; // Search offset (forwarded to engine)
    vespalib::string _ranking;   // ranking profile to be used
    uint32_t _randomSeed;        // seed for random rank values
    uint32_t _dateTime;      // datetime used for freshness boost

    FastS_query _query;     // NB: Here it is!

    FastS_QueryResult _queryResult;
    FastS_DocsumsResult _docsumsResult;
    FastS_SearchInfo _searchInfo;

    FastS_hitresult *_alignedHitBuf;    // Hits from engine
    bool     _hitbuf_needfree;  // Destructor should free _hitbuf.
    uint32_t _alignedHitCount;  // # Hits from engine

    uint32_t *_sortIndex;
    char     *_sortData; // NB: same malloc as _sortIndex
    bool      _sortdata_needfree;

public:
    static uint32_t _systemMaxHits;
    static uint32_t _maxOffset;
public:
    FastS_QueryCacheUtil();
    ~FastS_QueryCacheUtil();
    bool AgeDropCheck();
    void DropResult();
    bool GotNoResultsYet() const { return _queryResult._hitbuf == NULL; }
    uint32_t GetSearchOffset() const { return _searchInfo._searchOffset; }
    uint32_t GetMaxHits() const { return _searchInfo._maxHits; }
    uint32_t GetAlignedMaxHits() const { return _alignedMaxHits; }
    uint32_t GetAlignedSearchOffset() const { return _alignedSearchOffset; }
    const vespalib::string & GetRanking() const { return _ranking; }
    uint32_t GetRandomSeed() const { return _randomSeed; }
    uint32_t GetDateTime() const { return _dateTime; }
    FastS_query &GetQuery() { return _query; }
    const char *GetSortSpec() const { return _query.GetSortSpec(); }
    const char *GetLocation() const { return _query.GetLocation(); }
    bool ShouldDropSortData() const {
        return _query.IsFlagSet(search::fs4transport::QFLAG_DROP_SORTDATA);
    }
    bool IsQueryFlagSet(uint32_t flag) const { return _query.IsFlagSet(flag); }
    FastS_QueryResult *GetQueryResult() {
        return &_queryResult;
    }
    FastS_DocsumsResult *GetDocsumsResult() { return &_docsumsResult; }
    FastS_SearchInfo *GetSearchInfo() { return &_searchInfo; }
    void SetStartTime(double timeref) { _startTime = timeref; }
    void AdjustSearchParameters(uint32_t partitions);
    void AdjustSearchParametersFinal(uint32_t partitions);
    void SetupQuery(uint32_t maxhits, uint32_t offset);
    bool IsEstimate() const;
    void ForceStrictLimits();
    void InitEstimateMode();
    double ElapsedSecs(double now) const {
        double ret = now - _startTime;
        if (ret < 0.0)
            ret = 0.0;
        return ret;
    }
    void SetCoverage(uint64_t coverageDocs, uint64_t activeDocs, uint64_t soonActiveDocs,
                     uint32_t degradeReason, uint16_t nodesQueried, uint16_t nodesReplied)
    {
        _searchInfo._coverageDocs  = coverageDocs;
        _searchInfo._activeDocs  = activeDocs;
        _searchInfo._soonActiveDocs = soonActiveDocs;
        _searchInfo._degradeReason = degradeReason;
        _searchInfo._nodesQueried = nodesQueried;
        _searchInfo._nodesReplied = nodesReplied;
    }
    void SetAlignedHitCount(uint32_t alignedHitCount) {
        if (alignedHitCount > _alignedMaxHits) {
            alignedHitCount = _alignedMaxHits;
        }
        _alignedHitCount = alignedHitCount;
    }
    void CalcHitCount() {
        if (_alignedHitCount + _alignedSearchOffset > _searchInfo._searchOffset) {
            _queryResult._hitCount = _alignedHitCount + _alignedSearchOffset - _searchInfo._searchOffset;
        } else {
            _queryResult._hitCount = 0;
        }
        if (_queryResult._hitCount > _searchInfo._maxHits) {
            _queryResult._hitCount = _searchInfo._maxHits;
        }
    }
    void AllocAlignedHitBuf() {
        FastS_assert(_alignedHitBuf == NULL);
        if (_alignedHitCount != 0) {
            _alignedHitBuf = (FastS_hitresult*)malloc(sizeof(FastS_hitresult) * _alignedHitCount);
            _hitbuf_needfree = true;
            _queryResult._hitbuf = _alignedHitBuf + _searchInfo._searchOffset - _alignedSearchOffset;
        }
    }
    void AllocSortData(uint32_t sortDataLen)
    {
        FastS_assert(_sortIndex == NULL && _sortData == NULL);
        uint32_t hitcnt = _alignedHitCount;
        if (hitcnt == 0) {
            FastS_assert(sortDataLen == 0);
            return;
        }
        void *pt = malloc((hitcnt + 1) * sizeof(uint32_t) + sortDataLen);
        FastS_assert(pt != NULL);
        _sortIndex = (uint32_t *) pt;
        _sortData  = (char *)(void *)(_sortIndex + hitcnt + 1);
        _sortdata_needfree = true;
        if (hitcnt > _searchInfo._searchOffset) {
            _queryResult._sortIndex =
                _sortIndex + _searchInfo._searchOffset - _alignedSearchOffset;
            _queryResult._sortData = _sortData;
        }
    }
    uint32_t *GetSortIndex() const { return _sortIndex; }
    char *GetSortData() const { return _sortData; }
    FastS_hitresult *GetAlignedHitBuf() const { return _alignedHitBuf; }
    FastS_hitresult *GetAlignedHitBufEnd() const {
        return _alignedHitBuf + _alignedHitCount;
    }
    uint32_t GetAlignedHitCount() const { return _alignedHitCount; }
    void SetGroupResult(const char *groupResult) {
        _queryResult._groupResult = groupResult;
    }
    void SetGroupResultLen(uint32_t groupResultLen) {
        _queryResult._groupResultLen = groupResultLen;
    }
    void setSearchRequest(const search::engine::SearchRequest * request);
};

