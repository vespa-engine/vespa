// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchreply.h"

namespace search::engine {

SearchReply::SearchReply()
    : valid(true),
      offset(0),
      _distributionKey(0),
      totalHitCount(0),
      maxRank(0),
      sortIndex(),
      sortData(),
      groupResult(),
      coverage(),
      useWideHits(false),
      hits(),
      errorCode(0),
      errorMessage(),
      request()
{ }

SearchReply::~SearchReply() = default;

SearchReply::SearchReply(const SearchReply &rhs) :
    valid        (rhs.valid),
    offset       (rhs.offset),
    _distributionKey     (rhs._distributionKey),
    totalHitCount(rhs.totalHitCount),
    maxRank      (rhs.maxRank),
    sortIndex    (rhs.sortIndex),
    sortData     (rhs.sortData),
    groupResult  (rhs.groupResult),
    coverage     (rhs.coverage),
    useWideHits  (rhs.useWideHits),
    hits         (rhs.hits),
    errorCode    (rhs.errorCode),
    errorMessage (rhs.errorMessage),
    request() // NB not copied
{ }

}

