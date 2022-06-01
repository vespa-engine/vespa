// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "basic_nodes.h"
#include <vespa/vespalib/stllike/string.h>
#include <map>
#include <cmath>

namespace vespalib::eval { struct NodeVisitor; }

namespace vespalib::eval::nodes {

/**
 * Common superclass for AST nodes describing calls to built-in
 * functions. A call has a (function) name and a pre-defined number of
 * parameters that must be matched by the parsed expression.
 **/
class Call : public Node {
private:
    vespalib::string     _name;
    size_t               _num_params;
    std::vector<Node_UP> _args;
    bool                 _is_const_double;
public:
    Call(const vespalib::string &name_in, size_t num_params_in)
        : _name(name_in), _num_params(num_params_in), _is_const_double(false) {}
    ~Call();
    bool is_const_double() const override { return _is_const_double; }
    const vespalib::string &name() const { return _name; }
    size_t num_params() const { return _num_params; }
    size_t num_args() const { return _args.size(); }
    const Node &arg(size_t i) const { return *_args[i]; }
    size_t num_children() const override { return num_args(); }
    const Node &get_child(size_t idx) const override { return arg(idx); }
    void detach_children(NodeHandler &handler) override {
        for (size_t i = 0; i < _args.size(); ++i) {
            handler.handle(std::move(_args[i]));
        }
        _args.clear();
    }
    virtual void bind_next(Node_UP arg_in) {
        if (_args.empty()) {
            _is_const_double = arg_in->is_const_double();
        } else {
            _is_const_double = (_is_const_double && arg_in->is_const_double());
        }
        _args.push_back(std::move(arg_in));
    }
    vespalib::string dump(DumpContext &ctx) const override {
        vespalib::string str;
        str += _name;
        str += "(";
        for (size_t i = 0; i < _args.size(); ++i) {
            if (i > 0) {
                str += ",";
            }
            str += arg(i).dump(ctx);
        }
        str += ")";
        return str;
    }
};
typedef std::unique_ptr<Call> Call_UP;

//-----------------------------------------------------------------------------

/**
 * Repository for known built-in functions. This is used by the parser
 * to create appropriate call nodes by looking up function names.
 **/
class CallRepo {
private:
    static CallRepo _instance;
    typedef nodes::Call_UP (*factory_type)();
    std::map<vespalib::string,factory_type> _map;
    template <typename T>
    void add(const T &op) { _map[op.name()] = T::create; }
    CallRepo();
public:
    static const CallRepo &instance() { return _instance; }
    nodes::Call_UP create(const vespalib::string &name) const {
        auto result = _map.find(name);
        if (result != _map.end()) {
            return result->second();
        }
        return nodes::Call_UP(nullptr);
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
struct CallHelper : Call {
    typedef CallHelper<T> Helper;
    CallHelper(const vespalib::string &name_in, size_t num_params_in)
        : Call(name_in, num_params_in) {}
    void accept(NodeVisitor &visitor) const override;
    static Call_UP create() { return Call_UP(new T()); }
};

//-----------------------------------------------------------------------------

struct Cos : CallHelper<Cos> { Cos() : Helper("cos", 1) {} };
struct Sin : CallHelper<Sin> { Sin() : Helper("sin", 1) {} };
struct Tan : CallHelper<Tan> { Tan() : Helper("tan", 1) {} };
struct Cosh : CallHelper<Cosh> { Cosh() : Helper("cosh", 1) {} };
struct Sinh : CallHelper<Sinh> { Sinh() : Helper("sinh", 1) {} };
struct Tanh : CallHelper<Tanh> { Tanh() : Helper("tanh", 1) {} };
struct Acos : CallHelper<Acos> { Acos() : Helper("acos", 1) {} };
struct Asin : CallHelper<Asin> { Asin() : Helper("asin", 1) {} };
struct Atan : CallHelper<Atan> { Atan() : Helper("atan", 1) {} };
struct Exp : CallHelper<Exp> { Exp() : Helper("exp", 1) {} };
struct Log10 : CallHelper<Log10> { Log10() : Helper("log10", 1) {} };
struct Log : CallHelper<Log> { Log() : Helper("log", 1) {} };
struct Sqrt : CallHelper<Sqrt> { Sqrt() : Helper("sqrt", 1) {} };
struct Ceil : CallHelper<Ceil> { Ceil() : Helper("ceil", 1) {} };
struct Fabs : CallHelper<Fabs> { Fabs() : Helper("fabs", 1) {} };
struct Floor : CallHelper<Floor> { Floor() : Helper("floor", 1) {} };
struct Atan2 : CallHelper<Atan2> { Atan2() : Helper("atan2", 2) {} };
struct Ldexp : CallHelper<Ldexp> { Ldexp() : Helper("ldexp", 2) {} };
struct Pow2 : CallHelper<Pow2> { Pow2() : Helper("pow", 2) {} };
struct Fmod : CallHelper<Fmod> { Fmod() : Helper("fmod", 2) {} };
struct Min : CallHelper<Min> { Min() : Helper("min", 2) {} };
struct Max : CallHelper<Max> { Max() : Helper("max", 2) {} };
struct IsNan : CallHelper<IsNan> { IsNan() : Helper("isNan", 1) {} };
struct Relu : CallHelper<Relu> { Relu() : Helper("relu", 1) {} };
struct Sigmoid : CallHelper<Sigmoid> { Sigmoid() : Helper("sigmoid", 1) {} };
struct Elu : CallHelper<Elu> { Elu() : Helper("elu", 1) {} };
struct Erf : CallHelper<Erf> { Erf() : Helper("erf", 1) {} };
struct Bit : CallHelper<Bit> { Bit() : Helper("bit", 2) {} };
struct Hamming : CallHelper<Hamming> { Hamming() : Helper("hamming", 2) {} };

//-----------------------------------------------------------------------------

}
