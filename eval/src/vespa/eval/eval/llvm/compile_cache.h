// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "compiled_function.h"
#include <mutex>

namespace vespalib {
namespace eval {

/**
 * A compilation cache used to reduce application configuration cost
 * by not having to compile equivalent expressions multiple times. The
 * expression AST is used to produce a binary key that in turn is used
 * to query the cache. The cache itself will not keep anything alive,
 * but will let you find compiled functions that are currently in use
 * by others.
 **/
class CompileCache
{
private:
    typedef vespalib::string Key;
    struct Value {
        size_t num_refs;
        CompiledFunction cf;
        Value(CompiledFunction &&cf_in) : num_refs(1), cf(std::move(cf_in)) {}
    };
    typedef std::map<Key,Value> Map;
    static std::mutex _lock;
    static Map _cached;

    static void release(Map::iterator entry);

public:
    class Token
    {
    private:
        friend class CompileCache;
        CompileCache::Map::iterator entry;
        explicit Token(CompileCache::Map::iterator entry_in)
            : entry(entry_in) {}
    public:
        typedef std::unique_ptr<Token> UP;
        const CompiledFunction &get() const { return entry->second.cf; }
        ~Token() { CompileCache::release(entry); }
    };
    static Token::UP compile(const Function &function, PassParams pass_params);
    static size_t num_cached();
    static size_t count_refs();

private:
    struct CompileContext {
        const Function &function;
        PassParams pass_params;
        Token::UP token;
        CompileContext(const Function &function_in,
                       PassParams pass_params_in)
            : function(function_in),
              pass_params(pass_params_in),
              token() {}
    };

    static void do_compile(CompileContext &ctx);
};

} // namespace vespalib::eval
} // namespace vespalib

