// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchreply.h"

namespace search::engine {

SearchReply::SearchReply()
    : _distributionKey(0),
      totalHitCount(0),
      sortIndex(),
      sortData(),
      groupResult(),
      coverage(),
      hits(),
      match_features(),
      request(),
      my_issues()
{ }

SearchReply::~SearchReply() = default;

SearchReply::SearchReply(const SearchReply &rhs)
    : _distributionKey (rhs._distributionKey),
      totalHitCount    (rhs.totalHitCount),
      sortIndex        (rhs.sortIndex),
      sortData         (rhs.sortData),
      groupResult      (rhs.groupResult),
      coverage         (rhs.coverage),
      hits             (rhs.hits),
      match_features   (rhs.match_features),
      request(),       // NB not copied
      my_issues()      // NB not copied
{ }

}

