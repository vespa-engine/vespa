// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <mutex>
#include <vector>
#include <map>
#include <memory>

namespace vespalib::eval {

class ValueType;
class Function;

/**
 * Pre-computed index tables used by DenseLambdaPeekFunction. The
 * underlying index tables are shared between operations.
 **/
class IndexLookupTable
{
private:
    using Key = vespalib::string;
    struct Value {
        size_t num_refs;
        std::vector<uint32_t> data;
        struct ctor_tag {};
        Value(ctor_tag) : num_refs(1), data() {}
    };
    using Map = std::map<Key,Value>;
    static std::mutex _lock;
    static Map _cached;

    static void release(Map::iterator entry) {
        std::lock_guard<std::mutex> guard(_lock);
        if (--(entry->second.num_refs) == 0) {
            _cached.erase(entry);
        }
    }

public:
    /**
     * A token represents shared ownership of a cached index lookup
     * table.
     **/
    class Token
    {
    private:
        friend class IndexLookupTable;
        struct ctor_tag {};
        IndexLookupTable::Map::iterator _entry;
    public:
        Token(Token &&) = delete;
        Token(const Token &) = delete;
        Token &operator=(Token &&) = delete;
        Token &operator=(const Token &) = delete;
        using UP = std::unique_ptr<Token>;
        Token(IndexLookupTable::Map::iterator entry, ctor_tag) : _entry(entry) {}
        const std::vector<uint32_t> &get() const { return _entry->second.data; }
        ~Token() { IndexLookupTable::release(_entry); }
    };
    IndexLookupTable() = delete;
    static size_t num_cached();
    static size_t count_refs();
    static Token::UP create(const Function &idx_fun, const ValueType &type);
};

}
