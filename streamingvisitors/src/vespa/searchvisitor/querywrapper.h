// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/query/streaming/label_wrapper_query_node.h>
#include <vespa/searchlib/query/streaming/phrase_query_node.h>
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/searchlib/query/streaming/querynode.h>

namespace streaming {

/**
 * This class wraps a query and adds extra information to the list of leaf terms.
 **/
class QueryWrapper {
public:
    using TermList = search::streaming::QueryTermList;
    using LabelWrapperList = std::vector<search::streaming::LabelWrapperQueryNode*>;

private:
    search::streaming::Query& _query;
    TermList                  _termList;
    LabelWrapperList          _label_wrappers;

public:
    QueryWrapper(search::streaming::Query& query);
    ~QueryWrapper();
    TermList& getTermList() { return _termList; }
    const TermList& getTermList() const { return _termList; }
    LabelWrapperList& get_label_wrappers() { return _label_wrappers; }
    const LabelWrapperList& get_label_wrappers() const { return _label_wrappers; }
    search::streaming::Query& get_query() noexcept { return _query; }
};

} // namespace streaming
