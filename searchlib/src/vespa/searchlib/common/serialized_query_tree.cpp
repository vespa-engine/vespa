// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "serialized_query_tree.h"
#include <vespa/searchlib/engine/search_protocol_proto.h>
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/searchlib/query/from_proto.h>

namespace search {

namespace {
struct SdiWrap : SimpleQueryStackDumpIterator {
    SerializedQueryTreeSP ref;
    SdiWrap(SerializedQueryTreeSP data, std::string_view stackRef)
      : SimpleQueryStackDumpIterator(stackRef),
        ref(std::move(data))
    {}
    ~SdiWrap() = default;
};

struct PbiWrap : ProtoTreeIterator {
    SerializedQueryTreeSP ref;
    PbiWrap(SerializedQueryTreeSP data, const ProtobufQueryTree& proto_query_tree)
      : ProtoTreeIterator(proto_query_tree),
        ref(data)
    {}
    ~PbiWrap() = default;
};

} // namespace <unnamed>

SerializedQueryTree::SerializedQueryTree(std::vector<char> stackDump,
                                         std::unique_ptr<ProtobufQueryTree> protoQueryTree,
                                         ctor_tag)
  : _stackDump(std::move(stackDump)),
    _protoQueryTree(std::move(protoQueryTree))
{}

SerializedQueryTree::~SerializedQueryTree() = default;

SerializedQueryTreeSP SerializedQueryTree::fromStackDump(std::vector<char> stackDump) {
    std::unique_ptr<ProtobufQueryTree> dummy;
    ctor_tag tag;
    return std::make_shared<SerializedQueryTree>(std::move(stackDump), std::move(dummy), tag);
}

SerializedQueryTreeSP SerializedQueryTree::fromStackDump(std::string_view stackDumpRef) {
    std::vector<char> stackDump(stackDumpRef.data(), stackDumpRef.data() + stackDumpRef.size());
    return fromStackDump(std::move(stackDump));
}

SerializedQueryTreeSP SerializedQueryTree::fromProtobuf(std::unique_ptr<ProtobufQueryTree> protoQueryTree) {
    std::vector<char> dummy;
    ctor_tag tag;
    return std::make_shared<SerializedQueryTree>(std::move(dummy), std::move(protoQueryTree), tag);

}

std::unique_ptr<QueryStackIterator> SerializedQueryTree::makeIterator() const {
    if (_protoQueryTree) {
        return std::make_unique<PbiWrap>(shared_from_this(), *_protoQueryTree);
    } else {
        std::string_view stackRef(_stackDump.data(), _stackDump.size());
        return std::make_unique<SdiWrap>(shared_from_this(), stackRef);
    }
}

const SerializedQueryTree& SerializedQueryTree::empty() {
    static SerializedQueryTreeSP empty_instance = fromStackDump(std::vector<char>());
    return *empty_instance;
}

} // namespace search
