// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "basic_nodes.h"
#include "delete_node.h"
#include "value.h"

namespace vespalib::eval {

enum class PassParams : uint8_t { SEPARATE, ARRAY, LAZY };

/**
 * Interface used to perform custom symbol extraction. This is
 * typically used by the ranking framework to extend what will be
 * parsed as parameter names.
 **/
struct SymbolExtractor {
    virtual void extract_symbol(const char *pos_in, const char *end_in,
                                const char *&pos_out, std::string &symbol_out) const = 0;
    virtual ~SymbolExtractor() {}
};

struct NodeVisitor;

/**
 * When you parse an expression you get a Function. It contains the
 * AST root and the names of all parameters. A function can only be
 * evaluated using the appropriate number of parameters.
 **/
class Function : public std::enable_shared_from_this<Function>
{
private:
    nodes::Node_UP _root;
    std::vector<std::string> _params;

    struct ctor_tag {};

public:
    Function(nodes::Node_UP root_in, std::vector<std::string> params_in, ctor_tag) noexcept
        : _root(std::move(root_in)), _params(std::move(params_in)) {}
    Function(Function &&rhs) = delete;
    Function(const Function &rhs) = delete;
    Function &operator=(Function &&rhs) = delete;
    Function &operator=(const Function &rhs) = delete;
    ~Function() { delete_node(std::move(_root)); }
    size_t num_params() const { return _params.size(); }
    const std::string & param_name(size_t idx) const { return _params[idx]; }
    bool has_error() const;
    std::string get_error() const;
    const nodes::Node &root() const { return *_root; }
    static std::shared_ptr<Function const> create(nodes::Node_UP root_in, std::vector<std::string> params_in);
    static std::shared_ptr<Function const> parse(std::string_view expression);
    static std::shared_ptr<Function const> parse(std::string_view expression, const SymbolExtractor &symbol_extractor);
    static std::shared_ptr<Function const> parse(const std::vector<std::string> &params, std::string_view expression);
    static std::shared_ptr<Function const> parse(const std::vector<std::string> &params, std::string_view expression,
                     const SymbolExtractor &symbol_extractor);
    std::string dump() const {
        nodes::DumpContext dump_context(_params);
        return _root->dump(dump_context);
    }
    std::string dump_as_lambda() const;
    // Utility function used to unwrap an expression contained inside
    // a named wrapper. For example 'max(x+y)' -> 'max', 'x+y'
    static bool unwrap(std::string_view input,
                       std::string &wrapper,
                       std::string &body,
                       std::string &error);
    /**
     * Issues is used to report issues relating to the function
     * structure, typically to explain why a function cannot be
     * evaluated in a specific context due to it using features not
     * supported in that context.
     **/
    struct Issues {
        std::vector<std::string> list;
        operator bool() const noexcept { return !list.empty(); }
        Issues() noexcept : list() {}
        Issues(std::vector<std::string> &&list_in) noexcept : list(std::move(list_in)) {}
        void add_nested_issues(const std::string &context, const Issues &issues);
    };
};

}
