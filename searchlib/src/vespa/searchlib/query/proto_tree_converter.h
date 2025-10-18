// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/engine/search_protocol_proto.h>
#include <memory>

namespace search::query { class Node; }
namespace search {

using ProtobufQueryTree = ::searchlib::searchprotocol::protobuf::QueryTree;

template <class NodeTypes> class ProtoTreeConverterImpl;

/**
 * deserialize a QueryTree from protobuf to a templated Node tree.
 */
template <class NodeTypes>
struct ProtoTreeConverter {
    static std::unique_ptr<query::Node> convert(const ProtobufQueryTree& proto_query_tree) {
        using Converter = ProtoTreeConverterImpl<NodeTypes>;
        Converter impl(proto_query_tree);
        return impl.convert();
    }
};

} // namespace
