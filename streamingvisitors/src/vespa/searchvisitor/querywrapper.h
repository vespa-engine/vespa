// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/query/streaming/phrase_query_node.h>
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/searchlib/query/streaming/querynode.h>

namespace streaming {

/**
 * This class wraps a query and adds extra information to the list of leaf terms.
 **/
class QueryWrapper
{
public:
    using TermList = search::streaming::QueryTermList;

private:
    TermList   _termList;

public:
    QueryWrapper(search::streaming::Query & query);
    ~QueryWrapper();
    TermList & getTermList() { return _termList; }
    const TermList & getTermList() const { return _termList; }
};

} // namespace streaming

