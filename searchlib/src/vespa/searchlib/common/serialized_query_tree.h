// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <string_view>
#include <vector>

namespace searchlib::searchprotocol::protobuf { class QueryTree; }

namespace search {

class QueryStackIterator;
class SimpleQueryStackDumpIterator;
class SerializedQueryTree;

using SerializedQueryTreeSP = std::shared_ptr<const SerializedQueryTree>;

class SerializedQueryTree : public std::enable_shared_from_this<SerializedQueryTree> {
private:
    struct ctor_tag {};
public:
    using ProtobufQueryTree = ::searchlib::searchprotocol::protobuf::QueryTree;

    static SerializedQueryTreeSP fromStackDump(std::vector<char> stackDump);
    static SerializedQueryTreeSP fromStackDump(std::string_view stackDumpRef);
    static SerializedQueryTreeSP fromProtobuf(std::unique_ptr<ProtobufQueryTree> protoQueryTree);
    std::unique_ptr<QueryStackIterator> makeIterator() const;
    // use for testing only:
    std::string_view getStackRef() const noexcept {
        return std::string_view(_stackDump.data(), _stackDump.size());
    }

    SerializedQueryTree(std::vector<char> stackDump, std::unique_ptr<ProtobufQueryTree> protoQueryTree, ctor_tag tag);
    ~SerializedQueryTree();
    static const SerializedQueryTree& empty();

    auto apply(auto mapper) const {
        if (_protoQueryTree) {
            return mapper.fromProto(*_protoQueryTree);
        } else {
            auto iterator = makeIterator();
            return mapper.fromIterator(*iterator);
        }
    }
private:
    std::vector<char> _stackDump;
    std::unique_ptr<ProtobufQueryTree> _protoQueryTree;
};

} // namespace search
