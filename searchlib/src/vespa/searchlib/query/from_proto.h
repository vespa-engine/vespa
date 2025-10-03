// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "query_stack_iterator.h"
#include <vespa/searchlib/engine/search_protocol_proto.h>
#include <memory>

namespace search::query { class Node; }
namespace search {

using ProtobufQueryTree = ::searchlib::searchprotocol::protobuf::QueryTree;

class ProtoTreeIterator : public QueryStackIterator {
public:
    using QTI = ::searchlib::searchprotocol::protobuf::QueryTreeItem;
    using PWS = ::searchlib::searchprotocol::protobuf::ItemPureWeightedString;
    using PWL = ::searchlib::searchprotocol::protobuf::ItemPureWeightedLong;
    using TreeItem = std::variant<const QTI *, const PWS *, const PWL *>;
    explicit ProtoTreeIterator(const ProtobufQueryTree& proto_query_tree);
    ~ProtoTreeIterator();
    bool next() override;
private:
    const ProtobufQueryTree& _proto;
    std::vector<TreeItem> _items;
    uint32_t _pos;
    std::string _serialized_term;
    bool handle_item(TreeItem item);
    bool handle_item(const QTI& qsi);
};

} // namespace

#include "proto_tree_converter.hpp"

namespace search {

template <class NodeTypes>
struct ProtoTreeConverter {
    static std::unique_ptr<query::Node> convert(const ProtobufQueryTree& proto_query_tree) {
        ProtoTreeConverterImpl<NodeTypes> impl(proto_query_tree);
        return impl.convert();
    }
};

std::unique_ptr<query::Node> try_convert(const ProtobufQueryTree& proto_query_tree);

}
