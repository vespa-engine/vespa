// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "function.h"
#include "basic_nodes.h"
#include "tensor_nodes.h"
#include "operator_nodes.h"
#include "call_nodes.h"
#include "delete_node.h"
#include "aggr.h"
#include <vespa/vespalib/locale/c.h>
#include <cctype>
#include <map>

namespace vespalib::eval {

using nodes::Node_UP;
using nodes::Operator_UP;
using nodes::Call_UP;

namespace {

bool has_duplicates(const std::vector<vespalib::string> &list) {
    for (size_t i = 0; i < list.size(); ++i) {
        for (size_t j = (i + 1); j < list.size(); ++j) {
            if (list[i] == list[j]) {
                return true;
            }
        }
    }
    return false;
}

//-----------------------------------------------------------------------------

class Params {
private:
    std::map<vespalib::string,size_t> _params;
protected:
    size_t lookup(vespalib::stringref token) const {
        auto result = _params.find(token);
        return (result == _params.end()) ? UNDEF : result->second;
    }
    size_t lookup_add(vespalib::stringref token) {
        size_t result = lookup(token);
        if (result == UNDEF) {
            result = _params.size();
            _params[token] = result;
        }
        return result;
    }
public:
    static const size_t UNDEF = -1;
    virtual bool implicit() const = 0;
    virtual size_t resolve(vespalib::stringref token) const = 0;
    std::vector<vespalib::string> extract() const {
        std::vector<vespalib::string> params_out;
        params_out.resize(_params.size());
        for (const auto &item: _params) {
            params_out[item.second] = item.first;
        }
        return params_out;
    }
    virtual ~Params() {}
};

struct ExplicitParams : Params {
    explicit ExplicitParams(const std::vector<vespalib::string> &params_in) {
        for (const auto &param: params_in) {
            assert(lookup(param) == UNDEF);
            lookup_add(param);
        }
    }
    bool implicit() const override { return false; }
    size_t resolve(vespalib::stringref token) const override {
        return lookup(token);
    }
};

struct ImplicitParams : Params {
    bool implicit() const override { return true; }
    size_t resolve(vespalib::stringref token) const override {
        return const_cast<ImplicitParams*>(this)->lookup_add(token);
    }
};

//-----------------------------------------------------------------------------

class ResolveContext
{
private:
    const Params          &_params;
    const SymbolExtractor *_symbol_extractor;
public:
    ResolveContext(const Params &params, const SymbolExtractor *symbol_extractor)
        : _params(params), _symbol_extractor(symbol_extractor) {}
    size_t resolve_param(const vespalib::string &name) const { return _params.resolve(name); }
    const SymbolExtractor *symbol_extractor() const { return _symbol_extractor; }
};

class ParseContext
{
private:
    const char                  *_begin;
    const char                  *_pos;
    const char                  *_end;
    char                         _curr;
    vespalib::string             _scratch;
    vespalib::string             _failure;
    std::vector<Node_UP>         _expression_stack;
    std::vector<Operator_UP>     _operator_stack;
    size_t                       _operator_mark;
    std::vector<ResolveContext>  _resolve_stack;

public:
    ParseContext(const Params &params, const char *str, size_t len,
                 const SymbolExtractor *symbol_extractor)
        : _begin(str), _pos(str), _end(str + len), _curr(0),
          _scratch(), _failure(),
          _expression_stack(), _operator_stack(),
          _operator_mark(0),
          _resolve_stack({ResolveContext(params, symbol_extractor)})
    {
        if (_pos < _end) {
            _curr = *_pos;
        }
    }
    ~ParseContext() {
        for (size_t i = 0; i < _expression_stack.size(); ++i) {
            delete_node(std::move(_expression_stack[i]));
        }
        _expression_stack.clear();
    }

    ResolveContext &resolver() {
        assert(!_resolve_stack.empty());
        return _resolve_stack.back();
    }

    const ResolveContext &resolver() const {
        assert(!_resolve_stack.empty());
        return _resolve_stack.back();
    }

    void push_resolve_context(const Params &params, const SymbolExtractor *symbol_extractor) {
        _resolve_stack.emplace_back(params, symbol_extractor);
    }

    void pop_resolve_context() {
        assert(!_resolve_stack.empty());
        _resolve_stack.pop_back();
    }

    void fail(const vespalib::string &msg) {
        if (_failure.empty()) {
            _failure = msg;
            _curr = 0;
        }
    }
    bool failed() const { return !_failure.empty(); }
    void next() { _curr = (_curr && (_pos < _end)) ? *(++_pos) : 0; }

    struct InputMark {
        const char *pos;
        char curr;
    };

    InputMark get_input_mark() const { return InputMark{_pos, _curr}; }
    void restore_input_mark(InputMark mark) {
        if ((_curr == 0) && (mark.curr != 0)) {
            _failure.clear();
        }
        _pos = mark.pos;
        _curr = mark.curr;
    }

    char get() const { return _curr; }
    bool eos() const { return !_curr; }
    void eat(char c) {
        if (_curr == c) {
            next();
        } else {
            fail(make_string("expected '%c', but got '%c'", c, _curr));
        }
    }
    void skip_spaces() {
        while (!eos() && isspace(_curr)) {
            next();
        }
    }
    vespalib::string &scratch() {
        _scratch.clear();
        return _scratch;
    }
    vespalib::string &peek(vespalib::string &str, size_t n) {
        const char *p = _pos;
        for (size_t i = 0; i < n; ++i, ++p) {
            if (_curr != 0 && p < _end) {
                str.push_back(*p);
            } else {
                str.push_back(0);
            }
        }
        return str;
    }
    void skip(size_t n) {
        for (size_t i = 0; i < n; ++i) {
            next();
        }
    }

    size_t resolve_parameter(const vespalib::string &name) const {
        return resolver().resolve_param(name);
    }

    void extract_symbol(vespalib::string &symbol_out, InputMark before_symbol) {
        const SymbolExtractor *symbol_extractor = resolver().symbol_extractor();
        if (symbol_extractor == nullptr) {
            return;
        }
        symbol_out.clear();
        restore_input_mark(before_symbol);
        if (!eos()) {
            const char *new_pos = nullptr;
            symbol_extractor->extract_symbol(_pos, _end, new_pos, symbol_out);
            if ((new_pos != nullptr) && (new_pos > _pos) && (new_pos <= _end)) {
                _pos = new_pos;
                _curr = (_pos < _end) ? *_pos : 0;
            } else {
                symbol_out.clear();
            }
        }
    }

    Node_UP get_result() {
        if (!eos() || (num_expressions() != 1) || (num_operators() > 0)) {
            fail("incomplete parse");
        }
        if (!_failure.empty()) {
            vespalib::string before(_begin, (_pos - _begin));
            vespalib::string after(_pos, (_end - _pos));
            return Node_UP(new nodes::Error(make_string("[%s]...[%s]...[%s]",
                                    before.c_str(), _failure.c_str(), after.c_str())));
        }
        return pop_expression();
    }

    void apply_operator() {
        Operator_UP op = pop_operator();
        Node_UP rhs = pop_expression();
        Node_UP lhs = pop_expression();
        op->bind(std::move(lhs), std::move(rhs));
        push_expression(std::move(op));
    }
    size_t num_expressions() const { return _expression_stack.size(); }
    void push_expression(Node_UP node) {
        _expression_stack.push_back(std::move(node));
    }
    Node_UP pop_expression() {
        if (_expression_stack.empty()) {
            fail("expression stack underflow");
            return Node_UP(new nodes::Number(0.0));
        }
        Node_UP node = std::move(_expression_stack.back());
        _expression_stack.pop_back();
        return node;
    }
    size_t num_operators() const { return _operator_stack.size(); }

    size_t operator_mark() const { return _operator_mark; }
    void operator_mark(size_t mark) { _operator_mark = mark; }

    bool find_list_end() {
        skip_spaces();
        char c = get();
        return (c == /* eof */ '\0' || c == ')' || c == ']' || c == '}');
    }

    bool find_expression_end() {
        return (find_list_end() || (get() == ','));
    }

    size_t init_expression() {
        size_t old_mark = operator_mark();
        operator_mark(num_operators());
        return old_mark;
    }

    void fini_expression(size_t old_mark) {
        while (num_operators() > operator_mark()) {
            apply_operator();
        }
        operator_mark(old_mark);
    }

    void apply_until(const nodes::Operator &op) {
        while ((_operator_stack.size() > _operator_mark) &&
               (_operator_stack.back()->do_before(op)))
        {
            apply_operator();
        }
    }
    void push_operator(Operator_UP node) {
        apply_until(*node);
        _operator_stack.push_back(std::move(node));
    }
    Operator_UP pop_operator() {
        assert(!_operator_stack.empty());
        Operator_UP node = std::move(_operator_stack.back());
        _operator_stack.pop_back();
        return node;
    }
};

//-----------------------------------------------------------------------------

void parse_value(ParseContext &ctx);
void parse_expression(ParseContext &ctx);

Node_UP get_expression(ParseContext &ctx) {
    parse_expression(ctx);
    return ctx.pop_expression();
}

int unhex(char c) {
    if (c >= '0' && c <= '9') {
        return (c - '0');
    }
    if (c >= 'a' && c <= 'f') {
        return ((c - 'a') + 10);
    }
    if (c >= 'A' && c <= 'F') {
        return ((c - 'A') + 10);
    }
    return -1;
}

void parse_string(ParseContext &ctx) {
    vespalib::string &str = ctx.scratch();
    ctx.eat('"');
    while (!ctx.eos() && ctx.get() != '"') {
        if (ctx.get() == '\\') {
            ctx.next();
            if (ctx.get() == 'x') {
                ctx.next();
                int hex1 = unhex(ctx.get());
                ctx.next();
                int hex2 = unhex(ctx.get());
                if (hex1 < 0 || hex2 < 0) {
                    ctx.fail("bad hex quote");
                }
                str.push_back((hex1 << 4) + hex2);
            } else {
                switch(ctx.get()) {
                case '"':  str.push_back('"');  break;
                case '\\': str.push_back('\\'); break;
                case 'f':  str.push_back('\f'); break;
                case 'n':  str.push_back('\n'); break;
                case 'r':  str.push_back('\r'); break;
                case 't':  str.push_back('\t'); break;
                default: ctx.fail("bad quote"); break;
                }
            }
        } else {
            str.push_back(ctx.get()); // default case
        }
        ctx.next();
    }
    ctx.eat('"');
    ctx.push_expression(Node_UP(new nodes::String(str)));
}

void parse_number(ParseContext &ctx) {
    vespalib::string &str = ctx.scratch();
    str.push_back(ctx.get());
    ctx.next();
    while (ctx.get() >= '0' && ctx.get() <= '9') {
        str.push_back(ctx.get());
        ctx.next();
    }
    if (ctx.get() == '.') {
        str.push_back(ctx.get());
        ctx.next();
        while (ctx.get() >= '0' && ctx.get() <= '9') {
            str.push_back(ctx.get());
            ctx.next();
        }
    }
    if (ctx.get() == 'e' || ctx.get() == 'E') {
        str.push_back(ctx.get());
        ctx.next();
        if (ctx.get() == '+' || ctx.get() == '-') {
            str.push_back(ctx.get());
            ctx.next();
        }
        while (ctx.get() >= '0' && ctx.get() <= '9') {
            str.push_back(ctx.get());
            ctx.next();
        }
    }
    char *end = nullptr;
    double value = vespalib::locale::c::strtod(str.c_str(), &end);
    if (!str.empty() && end == str.data() + str.size()) {
        ctx.push_expression(Node_UP(new nodes::Number(value)));
    } else {
        ctx.fail(make_string("invalid number: '%s'", str.c_str()));
    }
}

// NOTE: using non-standard definition of identifiers
// (to match ranking expression parser in Java)
bool is_ident(char c, bool first) {
    return ((c >= 'a' && c <= 'z') ||
            (c >= 'A' && c <= 'Z') ||
            (c >= '0' && c <= '9') ||
            (c == '_') || (c == '@') ||
            (c == '$' && !first));
}

vespalib::string get_ident(ParseContext &ctx, bool allow_empty) {
    ctx.skip_spaces();
    vespalib::string ident;
    if (is_ident(ctx.get(), true)) {
        ident.push_back(ctx.get());
        for (ctx.next(); is_ident(ctx.get(), false); ctx.next()) {
            ident.push_back(ctx.get());
        }
    }
    if (!allow_empty && ident.empty()) {
        ctx.fail("missing identifier");
    }
    return ident;
}

size_t get_size_t(ParseContext &ctx) {
    ctx.skip_spaces();
    vespalib::string num;
    for (; isdigit(ctx.get()); ctx.next()) {
        num.push_back(ctx.get());
    }
    if (num.empty()) {
        ctx.fail("expected number");
    }
    return atoi(num.c_str());
}

void parse_if(ParseContext &ctx) {
    Node_UP cond = get_expression(ctx);
    ctx.eat(',');
    Node_UP true_expr = get_expression(ctx);
    ctx.eat(',');
    Node_UP false_expr = get_expression(ctx);
    double p_true = 0.5;
    if (ctx.get() == ',') {
        ctx.eat(',');
        parse_number(ctx);
        Node_UP p_true_node = ctx.pop_expression();
        auto p_true_number = nodes::as<nodes::Number>(*p_true_node);
        if (p_true_number) {
            p_true = p_true_number->value();
        }
    }
    ctx.push_expression(Node_UP(new nodes::If(std::move(cond), std::move(true_expr), std::move(false_expr), p_true)));
}

void parse_call(ParseContext &ctx, Call_UP call) {
    for (size_t i = 0; i < call->num_params(); ++i) {
        if (i > 0) {
            ctx.eat(',');
        }
        call->bind_next(get_expression(ctx));
    }
    ctx.push_expression(std::move(call));
}

// (a,b,c)     wrapped
// ,a,b,c -> ) not wrapped
std::vector<vespalib::string> get_ident_list(ParseContext &ctx, bool wrapped) {
    std::vector<vespalib::string> list;
    if (wrapped) {
        ctx.skip_spaces();
        ctx.eat('(');
    }
    while (!ctx.find_list_end()) {
        if (!list.empty() || !wrapped) {
            ctx.eat(',');
        }
        list.push_back(get_ident(ctx, false));
    }
    if (wrapped) {
        ctx.eat(')');
    }
    if (has_duplicates(list)) {
        ctx.fail("duplicate identifiers");
    }
    return list;
}

// a
// (a,b,c)
// cannot be empty
std::vector<vespalib::string> get_idents(ParseContext &ctx) {
    std::vector<vespalib::string> list;
    ctx.skip_spaces();
    if (ctx.get() == '(') {
        list = get_ident_list(ctx, true);
    } else {
        list.push_back(get_ident(ctx, false));
    }
    if (list.empty()) {
        ctx.fail("missing identifiers");        
    }
    return list;
}

Function parse_lambda(ParseContext &ctx, size_t num_params) {
    ctx.skip_spaces();
    ctx.eat('f');
    auto param_names = get_ident_list(ctx, true);
    ExplicitParams params(param_names);
    ctx.push_resolve_context(params, nullptr);
    ctx.skip_spaces();
    ctx.eat('(');
    Node_UP lambda_root = get_expression(ctx);
    ctx.eat(')');
    ctx.skip_spaces();
    ctx.pop_resolve_context();
    if (param_names.size() != num_params) {
        ctx.fail(make_string("expected lambda with %zu parameter(s), was %zu",
                             num_params, param_names.size()));
    }
    return Function(std::move(lambda_root), std::move(param_names));
}

void parse_tensor_map(ParseContext &ctx) {
    Node_UP child = get_expression(ctx);
    ctx.eat(',');
    Function lambda = parse_lambda(ctx, 1);
    ctx.push_expression(std::make_unique<nodes::TensorMap>(std::move(child), std::move(lambda)));
}

void parse_tensor_join(ParseContext &ctx) {
    Node_UP lhs = get_expression(ctx);
    ctx.eat(',');
    Node_UP rhs = get_expression(ctx);
    ctx.eat(',');
    Function lambda = parse_lambda(ctx, 2);
    ctx.push_expression(std::make_unique<nodes::TensorJoin>(std::move(lhs), std::move(rhs), std::move(lambda)));
}

void parse_tensor_reduce(ParseContext &ctx) {
    Node_UP child = get_expression(ctx);
    ctx.eat(',');
    auto aggr_name = get_ident(ctx, false);
    auto maybe_aggr = AggrNames::from_name(aggr_name);
    if (!maybe_aggr) {
        ctx.fail(make_string("unknown aggregator: '%s'", aggr_name.c_str()));
        return;
    }
    auto dimensions = get_ident_list(ctx, false);
    ctx.push_expression(std::make_unique<nodes::TensorReduce>(std::move(child), *maybe_aggr, std::move(dimensions)));
}

void parse_tensor_rename(ParseContext &ctx) {
    Node_UP child = get_expression(ctx);
    ctx.eat(',');
    auto from = get_idents(ctx);
    ctx.skip_spaces();
    ctx.eat(',');
    auto to = get_idents(ctx);
    if (from.size() != to.size()) {
        ctx.fail("dimension list size mismatch");
    } else {
        ctx.push_expression(std::make_unique<nodes::TensorRename>(std::move(child), std::move(from), std::move(to)));
    }
    ctx.skip_spaces();
}

// '{a:w,x:0}'
TensorSpec::Address get_tensor_address(ParseContext &ctx, const ValueType &type) {
    TensorSpec::Address addr;
    ctx.skip_spaces();
    ctx.eat('{');
    while (!ctx.find_list_end()) {
        if (!addr.empty()) {
            ctx.eat(',');
        }
        auto dim_name = get_ident(ctx, false);
        size_t dim_idx = type.dimension_index(dim_name);
        if (dim_idx != ValueType::Dimension::npos) {
            const auto &dim = type.dimensions()[dim_idx];
            ctx.skip_spaces();
            ctx.eat(':');
            if (dim.is_mapped()) {
                addr.emplace(dim_name, get_ident(ctx, false));
            } else {
                size_t idx = get_size_t(ctx);
                if (idx < dim.size) {
                    addr.emplace(dim_name, idx);
                } else {
                    ctx.fail(make_string("dimension index too large: %zu", idx));
                }
            }
        } else {
            ctx.fail(make_string("invalid dimension name: '%s'", dim_name.c_str()));
        }
    }
    ctx.eat('}');
    if (addr.size() != type.dimensions().size()) {
        ctx.fail(make_string("incomplete address: '%s'", as_string(addr).c_str()));
    }
    return addr;
}

// pre: 'tensor<float>(a{},x[3]):' -> type
// expect: '{{a:w,x:0}:1,{a:w,x:1}:2,{a:w,x:2}:3}'
void parse_tensor_create_verbose(ParseContext &ctx, const ValueType &type) {
    ctx.skip_spaces();
    ctx.eat('{');
    nodes::TensorCreate::Spec create_spec;
    while (!ctx.find_list_end()) {
        if (!create_spec.empty()) {
            ctx.eat(',');
        }
        auto address = get_tensor_address(ctx, type);
        ctx.skip_spaces();
        ctx.eat(':');
        create_spec.emplace(address, get_expression(ctx));
    }
    ctx.eat('}');
    ctx.push_expression(std::make_unique<nodes::TensorCreate>(type, std::move(create_spec)));
}

// pre: 'tensor<float>(a{},x[3]):' -> type
// expect: '{w:[0,1,2]}'
void parse_tensor_create_convenient(ParseContext &ctx, const ValueType &type,
                                    const std::vector<ValueType::Dimension> &dim_list)
{
    nodes::TensorCreate::Spec create_spec;
    using Label = TensorSpec::Label;
    std::vector<Label> addr;
    for (;;) {
        if (addr.size() == dim_list.size()) {
            TensorSpec::Address address;
            for (size_t i = 0; i < addr.size(); ++i) {
                if (addr[i].is_mapped()) {
                    address.emplace(dim_list[i].name, addr[i]);
                } else {
                    address.emplace(dim_list[i].name, Label(addr[i].index-1));
                }
            }
            create_spec.emplace(std::move(address), get_expression(ctx));
        } else {
            bool mapped = dim_list[addr.size()].is_mapped();
            addr.push_back(mapped ? Label("") : Label(size_t(0)));
            ctx.skip_spaces();
            ctx.eat(mapped ? '{' : '[');
        }
        while (ctx.find_list_end()) {
            bool mapped = addr.back().is_mapped();
            ctx.eat(mapped ? '}' : ']');
            addr.pop_back();
            if (addr.empty()) {
                return ctx.push_expression(std::make_unique<nodes::TensorCreate>(type, std::move(create_spec)));
            }
        }
        if (addr.back().is_mapped()) {
            if (addr.back().name != "") {
                ctx.eat(',');
            }
            addr.back().name = get_ident(ctx, false);
            ctx.skip_spaces();
            ctx.eat(':');
        } else {
            if (addr.back().index != 0) {
                ctx.eat(',');
            }
            if (++addr.back().index > dim_list[addr.size()-1].size) {
                return ctx.fail(make_string("dimension too large: '%s'",
                                            dim_list[addr.size()-1].name.c_str()));
            }
        }
    }
}

void parse_tensor_create(ParseContext &ctx, const ValueType &type,
                         const std::vector<ValueType::Dimension> &dim_list)
{
    ctx.skip_spaces();
    ctx.eat(':');
    ParseContext::InputMark before_cells = ctx.get_input_mark();
    ctx.skip_spaces();
    ctx.eat('{');
    ctx.skip_spaces();
    ctx.eat('{');
    bool is_verbose = !ctx.failed();
    ctx.restore_input_mark(before_cells);
    if (is_verbose) {
        parse_tensor_create_verbose(ctx, type);
    } else {
        parse_tensor_create_convenient(ctx, type, dim_list);
    }
}

void parse_tensor_lambda(ParseContext &ctx, const ValueType &type) {
    auto param_names = type.dimension_names();
    ExplicitParams params(param_names);
    ctx.push_resolve_context(params, nullptr);
    ctx.skip_spaces();
    ctx.eat('(');
    Function lambda(get_expression(ctx), std::move(param_names));
    ctx.eat(')');
    ctx.pop_resolve_context();
    ctx.push_expression(std::make_unique<nodes::TensorLambda>(std::move(type), std::move(lambda)));
}

bool maybe_parse_tensor_generator(ParseContext &ctx) {
    ParseContext::InputMark my_mark = ctx.get_input_mark();
    vespalib::string type_spec("tensor");
    while(!ctx.eos() && (ctx.get() != ')')) {
        type_spec.push_back(ctx.get());
        ctx.next();
    }
    ctx.eat(')');
    type_spec.push_back(')');
    std::vector<ValueType::Dimension> dim_list;
    ValueType type = ValueType::from_spec(type_spec, dim_list);
    ctx.skip_spaces();
    bool is_tensor_generate = ((ctx.get() == ':') || (ctx.get() == '('));
    if (!is_tensor_generate) {
        ctx.restore_input_mark(my_mark);
        return false;
    }
    bool is_create = (type.is_tensor() && (ctx.get() == ':'));
    bool is_lambda = (type.is_dense() && (ctx.get() == '('));
    if (is_create) {
        parse_tensor_create(ctx, type, dim_list);
    } else if (is_lambda) {
        parse_tensor_lambda(ctx, type);
    } else {
        ctx.fail("invalid tensor type");
    }
    return true;
}

void parse_tensor_concat(ParseContext &ctx) {
    Node_UP lhs = get_expression(ctx);
    ctx.eat(',');
    Node_UP rhs = get_expression(ctx);
    ctx.eat(',');
    auto dimension = get_ident(ctx, false);
    ctx.skip_spaces();
    ctx.push_expression(std::make_unique<nodes::TensorConcat>(std::move(lhs), std::move(rhs), dimension));
}

bool maybe_parse_call(ParseContext &ctx, const vespalib::string &name) {
    ctx.skip_spaces();
    if (ctx.get() == '(') {
        ctx.eat('(');
        if (name == "if") {
            parse_if(ctx);
        } else {
            Call_UP call = nodes::CallRepo::instance().create(name);
            if (call.get() != nullptr) {
                parse_call(ctx, std::move(call));
            } else if (name == "map") {
                parse_tensor_map(ctx);
            } else if (name == "join") {
                parse_tensor_join(ctx);
            } else if (name == "reduce") {
                parse_tensor_reduce(ctx);
            } else if (name == "rename") {
                parse_tensor_rename(ctx);
            } else if (name == "concat") {
                parse_tensor_concat(ctx);
            } else {
                ctx.fail(make_string("unknown function: '%s'", name.c_str()));
                return false;
            }
        }
        ctx.eat(')');
        return true;
    }
    return false;
}

size_t parse_symbol(ParseContext &ctx, vespalib::string &name, ParseContext::InputMark before_name) {
    ctx.extract_symbol(name, before_name);
    return ctx.resolve_parameter(name);
}

void parse_symbol_or_call(ParseContext &ctx) {
    ParseContext::InputMark before_name = ctx.get_input_mark();
    vespalib::string name = get_ident(ctx, true);
    bool was_tensor_generate = ((name == "tensor") && maybe_parse_tensor_generator(ctx));
    if (!was_tensor_generate && !maybe_parse_call(ctx, name)) {
        size_t id = parse_symbol(ctx, name, before_name);
        if (name.empty()) {
            ctx.fail("missing value");
        } else if (id == Params::UNDEF) {
            ctx.fail(make_string("unknown symbol: '%s'", name.c_str()));
        } else {
            ctx.push_expression(Node_UP(new nodes::Symbol(id)));
        }
    }
}

void parse_in(ParseContext &ctx)
{
    ctx.apply_until(nodes::Less());
    auto in = std::make_unique<nodes::In>(ctx.pop_expression());
    ctx.skip_spaces();
    ctx.eat('[');
    ctx.skip_spaces();
    size_t size = 0;
    while (!ctx.eos() && ctx.get() != ']') {
        if (++size > 1) {
            ctx.eat(',');
        }
        parse_value(ctx);
        ctx.skip_spaces();
        auto entry = ctx.pop_expression();
        auto num = nodes::as<nodes::Number>(*entry);
        auto str = nodes::as<nodes::String>(*entry);
        if (num || str) {
            in->add_entry(std::move(entry));
        } else {
            ctx.fail("invalid entry for 'in' operator");
        }
    }
    ctx.eat(']');
    ctx.push_expression(std::move(in));
}

void parse_value(ParseContext &ctx) {
    ctx.skip_spaces();
    if (ctx.get() == '-') {
        ctx.next();
        parse_value(ctx);
        auto entry = ctx.pop_expression();
        auto num = nodes::as<nodes::Number>(*entry);
        if (num) {
            ctx.push_expression(std::make_unique<nodes::Number>(-num->value()));
        } else {
            ctx.push_expression(std::make_unique<nodes::Neg>(std::move(entry)));
        }
    } else if (ctx.get() == '!') {
        ctx.next();
        parse_value(ctx);
        ctx.push_expression(Node_UP(new nodes::Not(ctx.pop_expression())));
    } else if (ctx.get() == '(') {
        ctx.next();
        parse_expression(ctx);
        ctx.eat(')');
    } else if (ctx.get() == '"') {
        parse_string(ctx);
    } else if (isdigit(ctx.get())) {
        parse_number(ctx);
    } else {
        parse_symbol_or_call(ctx);
    }
}

bool parse_operator(ParseContext &ctx) {
    bool expect_value = true;
    ctx.skip_spaces();
    vespalib::string &str = ctx.peek(ctx.scratch(), nodes::OperatorRepo::instance().max_size());
    Operator_UP op = nodes::OperatorRepo::instance().create(str);
    if (op.get() != nullptr) {
        ctx.push_operator(std::move(op));
        ctx.skip(str.size());
    } else {
        vespalib::string ident = get_ident(ctx, true);
        if (ident == "in") {
            parse_in(ctx);
            expect_value = false;
        } else {
            if (ident.empty()) {
                ctx.fail(make_string("invalid operator: '%c'", ctx.get()));
            } else {
                ctx.fail(make_string("invalid operator: '%s'", ident.c_str()));
            }
        }
    }
    return expect_value;
}

void parse_expression(ParseContext &ctx) {
    size_t old_mark = ctx.init_expression();
    bool expect_value = true;
    for (;;) {
        if (expect_value) {
            parse_value(ctx);
        }
        if (ctx.find_expression_end()) {
            return ctx.fini_expression(old_mark);
        }
        expect_value = parse_operator(ctx);
    }
}

Function parse_function(const Params &params, vespalib::stringref expression,
                        const SymbolExtractor *symbol_extractor)
{
    ParseContext ctx(params, expression.data(), expression.size(), symbol_extractor);
    parse_expression(ctx);
    if (ctx.failed() && params.implicit()) {
        return Function(ctx.get_result(), std::vector<vespalib::string>());
    }
    return Function(ctx.get_result(), params.extract());
}

} // namespace vespalib::<unnamed>

//-----------------------------------------------------------------------------

bool
Function::has_error() const
{
    auto error = nodes::as<nodes::Error>(*_root);
    return error;
}

vespalib::string
Function::get_error() const
{
    auto error = nodes::as<nodes::Error>(*_root);
    return error ? error->message() : "";
}

Function
Function::parse(vespalib::stringref expression)
{
    return parse_function(ImplicitParams(), expression, nullptr);
}

Function
Function::parse(vespalib::stringref expression, const SymbolExtractor &symbol_extractor)
{
    return parse_function(ImplicitParams(), expression, &symbol_extractor);
}

Function
Function::parse(const std::vector<vespalib::string> &params, vespalib::stringref expression)
{
    return parse_function(ExplicitParams(params), expression, nullptr);
}

Function
Function::parse(const std::vector<vespalib::string> &params, vespalib::stringref expression,
                const SymbolExtractor &symbol_extractor)
{
    return parse_function(ExplicitParams(params), expression, &symbol_extractor);
}

//-----------------------------------------------------------------------------

vespalib::string
Function::dump_as_lambda() const
{
    vespalib::string lambda = "f(";
    for (size_t i = 0; i < _params.size(); ++i) {
        if (i > 0) {
            lambda += ",";
        }
        lambda += _params[i];
    }
    lambda += ")";
    vespalib::string expr = dump();
    if (starts_with(expr, "(")) {
        lambda += expr;
    } else {
        lambda += "(";
        lambda += expr;
        lambda += ")";
    }
    return lambda;
}

bool
Function::unwrap(vespalib::stringref input,
                 vespalib::string &wrapper,
                 vespalib::string &body,
                 vespalib::string &error)
{
    size_t pos = 0;
    for (; pos < input.size() && isspace(input[pos]); ++pos);
    size_t wrapper_begin = pos;
    for (; pos < input.size() && isalpha(input[pos]); ++pos);
    size_t wrapper_end = pos;
    if (wrapper_end == wrapper_begin) {
        error = "could not extract wrapper name";
        return false;
    }
    for (; pos < input.size() && isspace(input[pos]); ++pos);
    if (pos == input.size() || input[pos] != '(') {
        error = "could not match opening '('";
        return false;
    }
    size_t body_begin = (pos + 1);
    size_t body_end = (input.size() - 1);
    for (; body_end > body_begin && isspace(input[body_end]); --body_end);
    if (input[body_end] != ')') {
        error = "could not match closing ')'";
        return false;
    }
    assert(body_end >= body_begin);
    wrapper = vespalib::stringref(input.data() + wrapper_begin, wrapper_end - wrapper_begin);
    body = vespalib::stringref(input.data() + body_begin, body_end - body_begin);
    return true;
}

//-----------------------------------------------------------------------------

}
