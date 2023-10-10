// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "onnx_wrapper.h"
#include <vespa/vespalib/stllike/string.h>
#include <memory>
#include <mutex>
#include <map>

namespace vespalib::eval {

/**
 * Cache used to share loaded onnx models between users. The cache
 * itself will not keep anything alive, but will let you find loaded
 * models that are currently in use by others.
 **/
class OnnxModelCache
{
private:
    struct ctor_tag {};
    using Key = vespalib::string;
    struct Value {
        size_t num_refs;
        std::unique_ptr<Onnx> model;
        Value(std::unique_ptr<Onnx> model_in) : num_refs(0), model(std::move(model_in)) {}
        const Onnx &get() { return *model; }
    };
    using Map = std::map<Key,Value>;
    static std::mutex _lock;
    static Map _cached;

    static void release(Map::iterator entry);

public:
    class Token
    {
    private:
        OnnxModelCache::Map::iterator _entry;
    public:
        Token(Token &&) = delete;
        Token(const Token &) = delete;
        Token &operator=(Token &&) = delete;
        Token &operator=(const Token &) = delete;
        using UP = std::unique_ptr<Token>;
        explicit Token(OnnxModelCache::Map::iterator entry, ctor_tag) : _entry(entry) {
            ++_entry->second.num_refs;
        }
        const Onnx &get() const { return _entry->second.get(); }
        ~Token() { OnnxModelCache::release(_entry); }
    };

    static Token::UP load(const vespalib::string &model_file);
    static size_t num_cached();
    static size_t count_refs();
};

}
