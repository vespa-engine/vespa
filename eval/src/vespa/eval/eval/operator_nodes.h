// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "basic_nodes.h"
#include <vespa/vespalib/stllike/string.h>
#include <cmath>
#include <memory>
#include <map>

namespace vespalib::eval { struct NodeVisitor; }

namespace vespalib::eval::nodes {

/**
 * Common superclass for AST nodes describing infix operators. Each
 * operator has a left hand side expression and a right hand side
 * expression. The parser will use Operator instances to resolve
 * precedence.
 **/
class Operator : public Node {
public:
    enum Order { LEFT, RIGHT };

private:
    vespalib::string _op_str;
    int              _priority;
    Order            _order;
    Node_UP          _lhs;
    Node_UP          _rhs;
    bool             _is_const_double;

public:
    Operator(const vespalib::string &op_str_in, int priority_in, Order order_in);
    ~Operator();
    vespalib::string op_str() const { return _op_str; }
    int priority() const { return _priority; }
    Order order() const { return _order; }
    const Node &lhs() const { return *_lhs; }
    const Node &rhs() const { return *_rhs; }
    bool is_const_double() const override { return _is_const_double; }
    size_t num_children() const override { return (_lhs && _rhs) ? 2 : 0; }
    const Node &get_child(size_t idx) const override {
        assert(idx < 2);
        return (idx == 0) ? lhs() : rhs();
    }
    void detach_children(NodeHandler &handler) override {
        handler.handle(std::move(_lhs));
        handler.handle(std::move(_rhs));
    }

    bool do_before(const Operator &other) {
        if (priority() > other.priority()) {
            return true;
        }
        if (other.priority() > priority()) {
            return false;
        }
        assert(order() == other.order());
        return (order() == LEFT);
    }

    virtual void bind(Node_UP lhs_in, Node_UP rhs_in) {
        _lhs = std::move(lhs_in);
        _rhs = std::move(rhs_in);
        _is_const_double = (_lhs->is_const_double() && _rhs->is_const_double());
    }

    vespalib::string dump(DumpContext &ctx) const override {
        vespalib::string str;
        str += "(";
        str += _lhs->dump(ctx);
        str += op_str();
        str += _rhs->dump(ctx);
        str += ")";
        return str;
    }
};
typedef std::unique_ptr<Operator> Operator_UP;

//-----------------------------------------------------------------------------

/**
 * Repository for known operators. This is used by the parser to
 * create appropriate operator nodes.
 **/
class OperatorRepo {
private:
    static OperatorRepo _instance;
    typedef nodes::Operator_UP (*factory_type)();
    std::map<vespalib::string,factory_type> _map;
    size_t _max_size;
    template <typename T>
    void add(const T &op) {
        vespalib::string op_str = op.op_str();
        _max_size = std::max(_max_size, op_str.size());
        _map[op_str] = T::create;
    }
    OperatorRepo();
public:
    static const OperatorRepo &instance() { return _instance; }
    size_t max_size() const { return _max_size; }
    nodes::Operator_UP create(vespalib::string &tmp) const {
        for (; !tmp.empty(); tmp.resize(tmp.size() - 1)) {
            auto result = _map.find(tmp);
            if (result != _map.end()) {
                return result->second();
            }
        }
        return nodes::Operator_UP(nullptr);
    }
    std::vector<vespalib::string> get_names() const {
        std::vector<vespalib::string> ret;
        for (const auto &entry: _map) {
            ret.push_back(entry.first);
        }
        return ret;
    }
};

//-----------------------------------------------------------------------------

template <typename T>
struct OperatorHelper : Operator {
    using Helper = OperatorHelper<T>;
    OperatorHelper(const vespalib::string &op_str_in, int priority_in, Operator::Order order_in)
        : Operator(op_str_in, priority_in, order_in) {}
    void accept(NodeVisitor &visitor) const override;
    static Operator_UP create() { return Operator_UP(new T()); }
};

//-----------------------------------------------------------------------------

class Add : public OperatorHelper<Add> {
private:
    bool _is_forest;
public:
    Add() : Helper("+", 101, LEFT), _is_forest(false) {}
    bool is_forest() const override { return _is_forest; }
    bool check_forest() const {
        bool lhs_ok = (lhs().is_tree() || lhs().is_forest());
        bool rhs_ok = (rhs().is_tree() || rhs().is_forest());
        return (lhs_ok && rhs_ok);
    }
    void bind(Node_UP lhs_in, Node_UP rhs_in) override {
        OperatorHelper<Add>::bind(std::move(lhs_in), std::move(rhs_in));
        _is_forest = check_forest();
    }
};

//-----------------------------------------------------------------------------

struct Sub          : OperatorHelper<Sub>          { Sub()          : Helper("-", 101, LEFT)  {}};
struct Mul          : OperatorHelper<Mul>          { Mul()          : Helper("*", 102, LEFT)  {}};
struct Div          : OperatorHelper<Div>          { Div()          : Helper("/", 102, LEFT)  {}};
struct Mod          : OperatorHelper<Mod>          { Mod()          : Helper("%", 102, LEFT)  {}};
struct Pow          : OperatorHelper<Pow>          { Pow()          : Helper("^", 103, RIGHT) {}};
struct Equal        : OperatorHelper<Equal>        { Equal()        : Helper("==", 10, LEFT)  {}};
struct NotEqual     : OperatorHelper<NotEqual>     { NotEqual()     : Helper("!=", 10, LEFT)  {}};
struct Approx       : OperatorHelper<Approx>       { Approx()       : Helper("~=", 10, LEFT)  {}};
struct Less         : OperatorHelper<Less>         { Less()         : Helper("<",  10, LEFT)  {}};
struct LessEqual    : OperatorHelper<LessEqual>    { LessEqual()    : Helper("<=", 10, LEFT)  {}};
struct Greater      : OperatorHelper<Greater>      { Greater()      : Helper(">",  10, LEFT)  {}};
struct GreaterEqual : OperatorHelper<GreaterEqual> { GreaterEqual() : Helper(">=", 10, LEFT)  {}};
struct And          : OperatorHelper<And>          { And()          : Helper("&&",  2, LEFT)  {}};
struct Or           : OperatorHelper<Or>           { Or()           : Helper("||",  1, LEFT)  {}};

//-----------------------------------------------------------------------------

}
