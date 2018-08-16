// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_operation.h"
#include <vespa/searchlib/attribute/singlenumericattribute.h>
#include <vespa/searchlib/common/resultset.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchcommon/attribute/basictype.h>

using search::attribute::BasicType;

namespace proton::matching {

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
    Add(T m) : _m(m) { }
    T _m;
    T operator()(T oldVal) const { return oldVal + _m; }
};

template <typename T>
struct Mul {
    using V = T;
    Mul(T m) : _m(m) { }
    T _m;
    T operator()(T oldVal) const { return oldVal * _m; }
};

template <typename T>
struct Div {
    using V = T;
    Div(T m) : _m(m) { }
    T _m;
    T operator()(T oldVal) const { return oldVal / _m; }
};

template <typename T>
struct Mod {
    using V = T;
    Mod(T m) : _m(m) { }
    T _m;
    T operator()(T oldVal) const { return oldVal % static_cast<int64_t>(_m); }
};

template <>
struct Mod<double> {
    using V = double;
    Mod(double ) { }
    double operator()(double oldVal) const { return oldVal; }
};

template <>
struct Mod<float> {
    using V = float;
    Mod(float ) { }
    float operator()(float oldVal) const { return oldVal; }
};

template <typename T>
struct Set {
    using V = T;
    Set(T m) : _m(m) { }
    T _m;
    T operator()(T) const { return  _m; }
};

template <typename T, typename OP>
struct UpdateFast {
    using A = search::SingleValueNumericAttribute<T>;
    using F = OP;
    A * attr;
    F op;
    typedef typename T::LoadedValueType ValueType;
    UpdateFast(search::attribute::IAttributeVector &attr_in, typename F::V operand)
        : attr(dynamic_cast<A *>(&attr_in)),
          op(operand)
    {}
    void operator()(uint32_t docid) { attr->set(docid, op(attr->getFast(docid))); }
    bool valid() const { return (attr != nullptr); }
};

template <typename OP>
class OperateOverResultSet : public AttributeOperation {
public:
    OperateOverResultSet(std::unique_ptr<search::ResultSet> result, typename OP::F::V operand)
        : _operand(operand),
          _result(std::move(result))
    {}

    void operator()(const search::AttributeVector &attributeVector) override {
        OP op(const_cast<search::AttributeVector &>(attributeVector), _operand);
        if (op.valid()) {
            search::RankedHit *hits      = _result->getArray();
            size_t             numHits   = _result->getArrayUsed();
            std::for_each(hits, hits+numHits,  [&op](search::RankedHit hit) { op(hit.getDocId()); });
            if (_result->getBitOverflow()) {
                _result->getBitOverflow()->foreach_truebit([&op](uint32_t docId) { op(docId); });
            }
        }
    }
private:
    typename OP::F::V _operand;
    std::unique_ptr<search::ResultSet> _result;
};

template<typename OP>
class OperateOverHits : public AttributeOperation {
public:
    using Hit = AttributeOperation::Hit;
    OperateOverHits(std::vector<Hit> reRanked, typename OP::F::V operand)
        : _operand(operand),
          _reRanked(std::move(reRanked))
    {}

    void operator()(const search::AttributeVector &attributeVector) override {
        OP op(const_cast<search::AttributeVector &>(attributeVector), _operand);
        if (op.valid()) {
            std::for_each(_reRanked.begin(), _reRanked.end(),
                          [&op](Hit hit) { op(hit.first); });
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

    void operator()(const search::AttributeVector &attributeVector) override {
        OP op(const_cast<search::AttributeVector &>(attributeVector), _operand);
        if (op.valid()) {
            std::for_each(_docIds.begin(), _docIds.end(),
                          [&op](uint32_t docId) { op(docId); });
        }
    }
private:
    typename OP::F::V _operand;
    std::vector<uint32_t> _docIds;
};

struct Operation {
    enum Type { INC, DEC, ADD, SUB, MUL, DIV, MOD, SET, BAD };
    Operation(Type operation_in, vespalib::stringref operand_in) : operation(operation_in), operand(operand_in) { }
    template <typename V>
    std::unique_ptr<AttributeOperation> create(search::attribute::BasicType type, V vector) const;
    template <typename IT, typename V>
    std::unique_ptr<AttributeOperation> create(V vector) const;
    Type operation;
    vespalib::stringref operand;
    static Operation create(vespalib::stringref s);
};

Operation
Operation::create(vespalib::stringref s)
{
    Type op = BAD;
    if (s.size() >= 2) {
        if ((s[0] == '+') && (s[1] == '+')) {
            op = INC;
        } else if ((s[0] == '-') && (s[1] == '-')) {
            op = DEC;
        } else if ((s[0] == '+') && (s[1] == '=')) {
            op = ADD;
        } else if ((s[0] == '-') && (s[1] == '=')) {
            op = SUB;
        } else if ((s[0] == '*') && (s[1] == '=')) {
            op = MUL;
        } else if ((s[0] == '/') && (s[1] == '=')) {
            op = DIV;
        } else if ((s[0] == '%') && (s[1] == '=')) {
            op = MOD;
        } else if (s[0] == '=') {
            op = SET;
        }
        if (op == SET) {
            s = s.substr(1);
        } else if (op == BAD) {
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
createOperation(std::unique_ptr<search::ResultSet> result, T operand) {
    return std::make_unique<OperateOverResultSet<OP>>(std::move(result), operand);
}

template <typename T_IN, typename V>
std::unique_ptr<AttributeOperation>
Operation::create(V vector) const {
    vespalib::asciistream is(operand);
    using T = typename T_IN::T;
    using A = typename T_IN::A;
    T value;
    is >> value;
    //TODO Add error handling
    switch (operation) {
        case INC:
            return createOperation<T, UpdateFast<A, Inc<T>>>(std::move(vector), value);
        case DEC:
            return createOperation<T, UpdateFast<A, Dec<T>>>(std::move(vector), value);
        case ADD:
            return createOperation<T, UpdateFast<A, Add<T>>>(std::move(vector), value);
        case SUB:
            return createOperation<T, UpdateFast<A, Add<T>>>(std::move(vector), -value);
        case MUL:
            return createOperation<T, UpdateFast<A, Mul<T>>>(std::move(vector), value);
        case DIV:
            return createOperation<T, UpdateFast<A, Div<T>>>(std::move(vector), value);
        case MOD:
            return createOperation<T, UpdateFast<A, Mod<T>>>(std::move(vector), value);
        case SET:
            return createOperation<T, UpdateFast<A, Set<T>>>(std::move(vector), value);
        default:
            return std::unique_ptr<AttributeOperation>();
    }
}

struct Int64T {
    using T = int64_t;
    using A = search::IntegerAttributeTemplate<int64_t>;
};

struct Int32T {
    using T = int64_t;
    using A = search::IntegerAttributeTemplate<int32_t>;
};

struct Int8T {
    using T = int64_t;
    using A = search::IntegerAttributeTemplate<int8_t>;
};

struct DoubleT {
    using T = double;
    using A = search::FloatingPointAttributeTemplate<double>;
};
struct FloatT {
    using T = double;
    using A = search::FloatingPointAttributeTemplate<float>;
};

template <typename V>
std::unique_ptr<AttributeOperation>
Operation::create(BasicType type, V hits) const {
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

std::unique_ptr<AttributeOperation>
AttributeOperation::create(BasicType type, const vespalib::string & operation, std::vector<uint32_t> docs) {
    Operation op = Operation::create(operation);
    using R = std::vector<uint32_t>;
    return op.create<R>(type, std::move(docs));
}

std::unique_ptr<AttributeOperation>
AttributeOperation::create(BasicType type, const vespalib::string & operation, std::vector<Hit> docs) {
    Operation op = Operation::create(operation);
    using R = std::vector<Hit>;
    return op.create<R>(type, std::move(docs));
}

std::unique_ptr<AttributeOperation>
AttributeOperation::create(BasicType type, const vespalib::string & operation, std::unique_ptr<search::ResultSet> docs) {
    Operation op = Operation::create(operation);
    using R = std::unique_ptr<search::ResultSet>;
    return op.create<R>(type, std::move(docs));
}

}