// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "serialized_query_tree.h"
#include <vespa/searchlib/parsequery/stackdumpiterator.h>

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

} // namespace <unnamed>

SerializedQueryTree::SerializedQueryTree(std::vector<char> stackDump, ctor_tag)
    : _stackDump(std::move(stackDump))
{}

SerializedQueryTree::~SerializedQueryTree() = default;

SerializedQueryTreeSP SerializedQueryTree::fromStackDump(std::vector<char> stackDump) {
    ctor_tag tag;
    return std::make_shared<SerializedQueryTree>(std::move(stackDump), tag);
}

SerializedQueryTreeSP SerializedQueryTree::fromStackDump(std::string_view stackDumpRef) {
    std::vector<char> stackDump(stackDumpRef.data(), stackDumpRef.data() + stackDumpRef.size());
    return fromStackDump(std::move(stackDump));
}

std::unique_ptr<QueryStackIterator> SerializedQueryTree::makeIterator() const {
    std::string_view stackRef(_stackDump.data(), _stackDump.size());
    return std::make_unique<SdiWrap>(shared_from_this(), stackRef);
}

SerializedQueryTreeSP SerializedQueryTree::_empty = SerializedQueryTree::fromStackDump(std::vector<char>());

SerializedQueryTreeSP SerializedQueryTree::empty() {
    return _empty;
}

} // namespace search
