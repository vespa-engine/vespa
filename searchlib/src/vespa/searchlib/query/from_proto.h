// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "query_stack_iterator.h"
#include <vespa/searchlib/engine/search_protocol_proto.h>

namespace search {

class ProtoStackIterator : public QueryStackIterator {
public:
    using ProtoQueryStack = ::searchlib::searchprotocol::protobuf::QueryStack;
    using StackItem = ::searchlib::searchprotocol::protobuf::QueryStackItem;
    explicit ProtoStackIterator(const ProtoQueryStack& proto_stack);
    ~ProtoStackIterator();
    bool next() override;
private:
    const ProtoQueryStack& _stack;
    std::vector<const StackItem *> _items;
    uint32_t _pos;
    std::string _serialized_term;
    bool handle_item(const StackItem& item);
};

}
