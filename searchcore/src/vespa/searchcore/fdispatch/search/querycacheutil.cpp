// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".search.querycacheutil");
#include <vespa/searchcore/util/log.h>

#include <vespa/searchlib/common/transport.h>
#include <vespa/searchlib/parsequery/simplequerystack.h>

#include <vespa/searchlib/common/sortdata.h>

#include <vespa/searchcore/fdispatch/search/querycacheutil.h>

using search::common::SortData;

uint32_t FastS_QueryCacheUtil::_systemMaxHits;
uint32_t FastS_QueryCacheUtil::_maxOffset = 4000;


FastS_QueryCacheUtil::FastS_QueryCacheUtil()
    : _startTime(),
      _userMaxHits(0),
      _alignedMaxHits(0),
      _alignedSearchOffset(0),
      _ranking(),
      _dateTime(0),
      _query(),
      _queryResult(),
      _docsumsResult(),
      _searchInfo(),
      _alignedHitBuf(NULL),
      _hitbuf_needfree(false),
      _alignedHitCount(0),
      _sortIndex(NULL),
      _sortData(NULL),
      _sortdata_needfree(false)
{
    _searchInfo._maxHits = 10;
}

FastS_QueryCacheUtil::~FastS_QueryCacheUtil()
{
}

void
FastS_QueryCacheUtil::setSearchRequest(const search::engine::SearchRequest * request)
{
    _ranking  = request->ranking;

    _query.SetQueryFlags(request->queryFlags);

    _query.SetStackDump(request->getStackRef());
    _query.SetSortSpec(request->sortSpec.c_str());
    _query._groupSpec = request->groupSpec;
    _query.SetLocation(request->location.c_str());
    _query.SetRankProperties(request->propertiesMap.rankProperties());
    _query.SetFeatureOverrides(request->propertiesMap.featureOverrides());
}


void
FastS_QueryCacheUtil::SetupQuery(uint32_t maxhits,
				 uint32_t offset)
{
    FastS_assert(_queryResult._hitbuf == NULL);
    FastS_assert(_alignedHitBuf == NULL);
    FastS_assert(!_hitbuf_needfree);
    FastS_assert(_queryResult._hitCount == 0);
    FastS_assert(_docsumsResult._fullResultCount == 0);
    FastS_assert(_alignedHitCount == 0);
    FastS_assert(_queryResult._totalHitCount == 0);
    FastS_assert(_alignedMaxHits == 0);
    FastS_assert(_alignedSearchOffset == 0);
    FastS_assert(_docsumsResult._fullresult == NULL);
    _searchInfo._searchOffset = offset;
    _searchInfo._maxHits = maxhits;
}


void
FastS_QueryCacheUtil::AdjustSearchParameters(uint32_t partitions)
{
    bool strict = (partitions > 1);

    if (_searchInfo._maxHits == 0) {
        _searchInfo._searchOffset = 0;
    }

    _searchInfo._maxHits = std::min(_searchInfo._maxHits, _maxOffset + _systemMaxHits);
    if (strict) {
        _searchInfo._searchOffset = std::min(_searchInfo._searchOffset, _maxOffset);
        _searchInfo._maxHits = std::min(_searchInfo._maxHits, _maxOffset + _systemMaxHits - _searchInfo._searchOffset);
    }
}


void
FastS_QueryCacheUtil::AdjustSearchParametersFinal(uint32_t partitions)
{
    if (IsEstimate()) {

        FastS_assert(_searchInfo._searchOffset == 0);
        FastS_assert(_searchInfo._maxHits  == 0);

        _alignedSearchOffset = 0;
        _alignedMaxHits      = 0;
    } else {
        _alignedSearchOffset = (partitions > 1) ? 0 : _searchInfo._searchOffset;
        _alignedMaxHits = _searchInfo._maxHits + _searchInfo._searchOffset - _alignedSearchOffset;
        FastS_assert(_alignedMaxHits <= _maxOffset + _systemMaxHits);
    }
}

void
FastS_QueryCacheUtil::DropResult()
{
    _queryResult._groupResultLen = 0;
    _queryResult._groupResult = NULL;

    if (_hitbuf_needfree) {
        FastS_assert(_alignedHitBuf != NULL);
        free(_alignedHitBuf);
    }
    if (_sortdata_needfree) {
        FastS_assert(_sortIndex != NULL);
        free(_sortIndex);
    }
    _sortIndex = NULL;
    _sortData = NULL;
    _sortdata_needfree = false;
    _alignedHitBuf = NULL;
    _queryResult._hitbuf = NULL;
    _hitbuf_needfree = false;
    free(_docsumsResult._fullresult);
    _docsumsResult._fullresult = NULL;
    _queryResult._hitCount = 0;
    _docsumsResult._fullResultCount = 0;
    _queryResult._totalHitCount = 0;
    _queryResult._maxRank = std::numeric_limits<search::HitRank>::is_integer ?
                            std::numeric_limits<search::HitRank>::min() :
                            - std::numeric_limits<search::HitRank>::max();

    _alignedHitCount = 0;
}


bool
FastS_QueryCacheUtil::IsEstimate() const
{
    return _query.IsFlagSet(search::fs4transport::QFLAG_ESTIMATE);
}

void
FastS_QueryCacheUtil::InitEstimateMode()
{
    _searchInfo._searchOffset = 0;
    _searchInfo._maxHits  = 0;
    _ranking.clear();
    _dateTime	= 0;
}
