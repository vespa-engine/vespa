// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "query_stack_iterator.h"
#include <vespa/searchlib/engine/search_protocol_proto.h>

namespace search {

class ProtoTreeIterator : public QueryStackIterator {
public:
    using ProtoQueryTree = ::searchlib::searchprotocol::protobuf::QueryTree;
    using TreeItem = ::searchlib::searchprotocol::protobuf::QueryTreeItem;
    explicit ProtoTreeIterator(const ProtoQueryTree& proto_query_tree);
    ~ProtoTreeIterator();
    bool next() override;
private:
    const ProtoQueryTree& _proto;
    std::vector<const TreeItem *> _items;
    uint32_t _pos;
    std::string _serialized_term;
    bool handle_item(const TreeItem& item);
};

}
