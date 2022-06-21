// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_operation.h"
#include "singlenumericattribute.h"
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchcommon/attribute/basictype.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/array.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.attribute_operation");

namespace search::attribute {

namespace {

template <typename T>
struct Inc {
    using V = T;
    Inc(T ) {}
    T operator()(T oldVal) const { return oldVal + 1; }
};

template <typename T>
struct Dec {
    using V = T;
    Dec(T ) {}
    T operator()(T oldVal) const { return oldVal - 1; }
};

template <typename T>
struct Add {
    using V = T;
    Add(T m) : _m(m) {}
    T _m;
    T operator()(T oldVal) const { return oldVal + _m; }
};

template <typename T>
struct Mul {
    using V = T;
    Mul(T m) : _m(m) {}
    T _m;
    T operator()(T oldVal) const { return oldVal * _m; }
};

template <typename T>
struct Div {
    using V = T;
    Div(T m) : _m(m) {}
    T _m;
    T operator()(T oldVal) const { return oldVal / _m; }
};

template <typename T>
struct Mod {
    using V = T;
    Mod(T m) : _m(m) {}
    T _m;
    T operator()(T oldVal) const { return oldVal % static_cast<int64_t>(_m); }
};

template <>
struct Mod<double> {
    using V = double;
    Mod(double ) {}
    double operator()(double oldVal) const { return oldVal; }
};

template <>
struct Mod<float> {
    using V = float;
    Mod(float ) {}
    float operator()(float oldVal) const { return oldVal; }
};

template <typename T>
struct Set {
    using V = T;
    Set(T m) : _m(m) {}
    T _m;
    T operator()(T) const { return  _m; }
};

template <typename T, typename OP>
struct UpdateFast {
    using A = SingleValueNumericAttribute<T>;
    using F = OP;
    A * attr;
    F op;
    typedef typename T::LoadedValueType ValueType;
    UpdateFast(IAttributeVector &attr_in, typename F::V operand)
        : attr(dynamic_cast<A *>(&attr_in)),
          op(operand)
    {}
    void operator()(uint32_t docid) { attr->set(docid, op(attr->getFast(docid))); }
    bool valid() const {
        return (attr != nullptr) &&
               (attr->isMutable()); }
};

template <typename OP>
class OperateOverResultSet : public AttributeOperation {
public:
    OperateOverResultSet(FullResult && result, typename OP::F::V operand)
        : _operand(operand),
          _result(std::move(result))
    {}

    void operator()(IAttributeVector &attributeVector) override {
        OP op(attributeVector, _operand);
        if (op.valid()) {
            const RankedHit *hits = _result.second.data();
            size_t numHits   = _result.second.size();
            std::for_each(hits, hits+numHits,  [&op](RankedHit hit) { op(hit.getDocId()); });
            if (_result.first) {
                _result.first->foreach_truebit([&op](uint32_t docId) { op(docId); });
            }
        }
    }
private:
    typename OP::F::V _operand;
    FullResult _result;
};

template<typename OP>
class OperateOverHits : public AttributeOperation {
public:
    using Hit = AttributeOperation::Hit;
    OperateOverHits(std::vector<Hit> reRanked, typename OP::F::V operand)
        : _operand(operand),
          _reRanked(std::move(reRanked))
    {}

    void operator()(IAttributeVector &attributeVector) override {
        OP op(attributeVector, _operand);
        if (op.valid()) {
            std::for_each(_reRanked.begin(), _reRanked.end(), [&op](Hit hit) { op(hit.first); });
        }
    }
private:
    typename OP::F::V _operand;
    std::vector<Hit> _reRanked;
};

template<typename OP>
class OperateOverDocIds : public AttributeOperation {
public:
    OperateOverDocIds(std::vector<uint32_t> docIds, typename OP::F::V operand)
        : _operand(operand),
          _docIds(std::move(docIds))
    {}

    void operator()(IAttributeVector &attributeVector) override {
        OP op(attributeVector, _operand);
        if (op.valid()) {
            std::for_each(_docIds.begin(), _docIds.end(), [&op](uint32_t docId) { op(docId); });
        }
    }
private:
    typename OP::F::V _operand;
    std::vector<uint32_t> _docIds;
};

struct Operation {
    enum class Type { INC, DEC, ADD, SUB, MUL, DIV, MOD, SET, BAD };
    Operation(Type operation_in, vespalib::stringref operand_in) : operation(operation_in), operand(operand_in) { }
    template <typename V>
    std::unique_ptr<AttributeOperation> create(BasicType type, V vector) const;
    template <typename IT, typename V>
    std::unique_ptr<AttributeOperation> create(V vector) const;
    bool valid() const { return operation != Type::BAD; }
    bool hasArgument() const { return valid() && (operation != Type::INC) && (operation != Type::DEC); }
    Type operation;
    vespalib::stringref operand;
    static Operation create(vespalib::stringref s);
};

Operation
Operation::create(vespalib::stringref s)
{
    Type op = Type::BAD;
    if (s.size() >= 2) {
        if ((s[0] == '+') && (s[1] == '+')) {
            op = Type::INC;
        } else if ((s[0] == '-') && (s[1] == '-')) {
            op = Type::DEC;
        } else if ((s[0] == '+') && (s[1] == '=')) {
            op = Type::ADD;
        } else if ((s[0] == '-') && (s[1] == '=')) {
            op = Type::SUB;
        } else if ((s[0] == '*') && (s[1] == '=')) {
            op = Type::MUL;
        } else if ((s[0] == '/') && (s[1] == '=')) {
            op = Type::DIV;
        } else if ((s[0] == '%') && (s[1] == '=')) {
            op = Type::MOD;
        } else if (s[0] == '=') {
            op = Type::SET;
        }
        if (op == Type::SET) {
            s = s.substr(1);
        } else if (op == Type::BAD) {
        } else {
            s = s.substr(2);
        }
    }
    return Operation(op, s);
}

template<typename T, typename OP>
std::unique_ptr<AttributeOperation>
createOperation(std::vector<uint32_t> vector, T operand) {
    return std::make_unique<OperateOverDocIds<OP>>(std::move(vector), operand);
}

template<typename T, typename OP>
std::unique_ptr<AttributeOperation>
createOperation(std::vector<AttributeOperation::Hit> vector, T operand) {
    return std::make_unique<OperateOverHits<OP>>(std::move(vector), operand);
}

template<typename T, typename OP>
std::unique_ptr<AttributeOperation>
createOperation(AttributeOperation::FullResult && result, T operand) {
    return std::make_unique<OperateOverResultSet<OP>>(std::move(result), operand);
}

template <typename T_IN, typename V>
std::unique_ptr<AttributeOperation>
Operation::create(V vector) const {
    using T = typename T_IN::T;
    using A = typename T_IN::A;
    T value(0);
    Type validOp = operation;
    if (hasArgument()) {
        vespalib::asciistream is(operand);
        try {
            is >> value;
            if (!is.empty()) {
                LOG(warning, "Invalid operand, unable to consume all of (%s). (%s) is unconsumed.", operand.data(), is.c_str());
                validOp = Type::BAD;
            }
            if (((validOp == Type::DIV) || (validOp == Type::MOD)) && (value == 0)) {
                LOG(warning, "Division by zero is not acceptable (%s).", operand.data());
                validOp = Type::BAD;
            }
        } catch (vespalib::IllegalArgumentException & e) {
            LOG(warning, "Invalid operand, ignoring : %s", e.what());
            validOp = Type::BAD;
        }
    }
    switch (validOp) {
        case Type::INC:
            return createOperation<T, UpdateFast<A, Inc<T>>>(std::move(vector), value);
        case Type::DEC:
            return createOperation<T, UpdateFast<A, Dec<T>>>(std::move(vector), value);
        case Type::ADD:
            return createOperation<T, UpdateFast<A, Add<T>>>(std::move(vector), value);
        case Type::SUB:
            return createOperation<T, UpdateFast<A, Add<T>>>(std::move(vector), -value);
        case Type::MUL:
            return createOperation<T, UpdateFast<A, Mul<T>>>(std::move(vector), value);
        case Type::DIV:
            return createOperation<T, UpdateFast<A, Div<T>>>(std::move(vector), value);
        case Type::MOD:
            return createOperation<T, UpdateFast<A, Mod<T>>>(std::move(vector), value);
        case Type::SET:
            return createOperation<T, UpdateFast<A, Set<T>>>(std::move(vector), value);
        default:
            return std::unique_ptr<AttributeOperation>();
    }
}

struct Int64T {
    using T = int64_t;
    using A = IntegerAttributeTemplate<int64_t>;
};

struct Int32T {
    using T = int64_t;
    using A = IntegerAttributeTemplate<int32_t>;
};

struct Int8T {
    using T = int64_t;
    using A = IntegerAttributeTemplate<int8_t>;
};

struct DoubleT {
    using T = double;
    using A = FloatingPointAttributeTemplate<double>;
};
struct FloatT {
    using T = double;
    using A = FloatingPointAttributeTemplate<float>;
};

template <typename V>
std::unique_ptr<AttributeOperation>
Operation::create(BasicType type, V hits) const {
    if ( ! valid()) {
        return std::unique_ptr<AttributeOperation>();
    }
    switch (type.type()) {
        case BasicType::INT64:
            return create<Int64T, V>(std::move(hits));
        case BasicType::INT32:
            return create<Int32T, V>(std::move(hits));
        case BasicType::INT8:
            return create<Int8T, V>(std::move(hits));
        case BasicType::DOUBLE:
            return create<DoubleT, V>(std::move(hits));
        case BasicType::FLOAT:
            return create<FloatT, V>(std::move(hits));
        default:
            return std::unique_ptr<AttributeOperation>();
    }
}

}

template <typename Hits>
std::unique_ptr<AttributeOperation>
AttributeOperation::create(BasicType type, const vespalib::string & operation, Hits docs) {
    Operation op = Operation::create(operation);
    return op.create<Hits>(type, std::move(docs));
}

template std::unique_ptr<AttributeOperation>
AttributeOperation::create(BasicType, const vespalib::string &, std::vector<uint32_t>);

template std::unique_ptr<AttributeOperation>
AttributeOperation::create(BasicType, const vespalib::string &, std::vector<Hit>);

template std::unique_ptr<AttributeOperation>
AttributeOperation::create(BasicType, const vespalib::string &, FullResult);

}

template class vespalib::Array<search::RankedHit>;
