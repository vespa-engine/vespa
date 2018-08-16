// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_operation.h"
#include <vespa/searchlib/attribute/singlenumericattribute.h>
#include <vespa/searchlib/common/resultset.h>
#include <vespa/searchlib/common/bitvector.h>

namespace proton::matching {

namespace {

template <typename T>
struct Inc {
    T operator()(T oldVal) const { return oldVal + 1; }
};

template <typename T>
struct Add {
    Add(T m) : _m(m) { }
    T _m;
    T operator()(T oldVal) const { return oldVal + _m; }
};

template <typename T>
struct Mul {
    Mul(T m) : _m(m) { }
    T _m;
    T operator()(T oldVal) const { return oldVal * _m; }
};

template <typename T, typename OP>
struct UpdateFast {
    using A = search::SingleValueNumericAttribute<T>;
    using F = OP;
    A * attr;
    F op;
    typedef typename T::LoadedValueType ValueType;
    UpdateFast(F op_in) : attr(nullptr), op(op_in) {}
    void init(search::attribute::IAttributeVector &attr_in) { attr = dynamic_cast<A *>(&attr_in); }
    void operator()(uint32_t docid) { attr->set(docid, op(attr->getFast(docid))); }
    bool valid() const { return (attr != nullptr); }
};

using IncrementOP = UpdateFast<search::IntegerAttributeTemplate<int64_t>, Inc<int64_t>>;

template <typename OP>
class OperateOverResultSet : public AttributeOperation {
public:
    OperateOverResultSet(std::unique_ptr<search::ResultSet> result, typename OP::F op)
        : _op(op),
          _result(std::move(result))
    {}

    void operator()(const search::AttributeVector &attributeVector) override {
        _op.init(const_cast<search::AttributeVector &>(attributeVector));
        if (_op.valid()) {
            search::RankedHit *hits      = _result->getArray();
            size_t             numHits   = _result->getArrayUsed();
            std::for_each(hits, hits+numHits,  [&](search::RankedHit hit) { _op(hit.getDocId()); });
            if (_result->getBitOverflow()) {
                _result->getBitOverflow()->foreach_truebit([&](uint32_t docId) { _op(docId); });
            }
        }
    }
private:
    OP _op;
    std::unique_ptr<search::ResultSet> _result;
};

template<typename OP>
class OperateOverHits : public AttributeOperation {
public:
    using Hit = AttributeOperation::Hit;
    OperateOverHits(std::vector<Hit> reRanked, typename OP::F op)
        : _op(op),
          _reRanked(std::move(reRanked))
    {}

    void operator()(const search::AttributeVector &attributeVector) override {
        _op.init(const_cast<search::AttributeVector &>(attributeVector));
        if (_op.valid()) {
            std::for_each(_reRanked.begin(), _reRanked.end(),
                          [&](Hit hit) { _op(hit.first); });
        }
    }
private:
    OP _op;
    std::vector<Hit> _reRanked;
};

template<typename OP>
class OperateOverDocIds : public AttributeOperation {
public:
    OperateOverDocIds(std::vector<uint32_t> docIds, typename OP::F op)
        : _op(op),
          _docIds(std::move(docIds))
    {}

    void operator()(const search::AttributeVector &attributeVector) override {
        _op.init(const_cast<search::AttributeVector &>(attributeVector));
        if (_op.valid()) {
            std::for_each(_docIds.begin(), _docIds.end(),
                          [&](uint32_t docId) { _op(docId); });
        }
    }
private:
    OP _op;
    std::vector<uint32_t> _docIds;
};

}

std::unique_ptr<AttributeOperation>
AttributeOperation::create(const vespalib::string & operation, std::vector<uint32_t> docIds) {
    (void) operation;
    return std::make_unique<OperateOverDocIds<IncrementOP>>(std::move(docIds), IncrementOP::F());

}

std::unique_ptr<AttributeOperation>
AttributeOperation::create(const vespalib::string & operation, std::vector<Hit> hits) {
    (void) operation;
    return std::make_unique<OperateOverHits<IncrementOP>>(std::move(hits), IncrementOP::F());
}

std::unique_ptr<AttributeOperation>
AttributeOperation::create(const vespalib::string & operation, std::unique_ptr<search::ResultSet> result) {
    (void) operation;
    return std::make_unique<OperateOverResultSet<IncrementOP>>(std::move(result), IncrementOP::F());
}

}