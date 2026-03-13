// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_updater.h"
#include <vespa/document/base/forcelink.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/literalfieldvalue.h>
#include <vespa/document/fieldvalue/predicatefieldvalue.h>
#include <vespa/document/fieldvalue/rawfieldvalue.h>
#include <vespa/document/fieldvalue/referencefieldvalue.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/update/addvalueupdate.h>
#include <vespa/document/update/arithmeticvalueupdate.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/update/clearvalueupdate.h>
#include <vespa/document/update/mapvalueupdate.h>
#include <vespa/document/update/removevalueupdate.h>
#include <vespa/document/update/tensor_add_update.h>
#include <vespa/document/update/tensor_modify_update.h>
#include <vespa/document/update/tensor_remove_update.h>
#include <vespa/eval/eval/value.h>
#include <vespa/searchlib/attribute/array_bool_attribute.h>
#include <vespa/searchlib/attribute/attributevector.hpp>
#include <vespa/searchlib/attribute/changevector.hpp>
#include <vespa/searchlib/attribute/predicate_attribute.h>
#include <vespa/searchlib/attribute/reference_attribute.h>
#include <vespa/searchlib/attribute/single_raw_attribute.h>
#include <vespa/searchlib/tensor/tensor_attribute.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/classname.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".proton.common.attribute_updater");

using namespace document;
using vespalib::make_string;
using vespalib::getClassName;
using search::tensor::PrepareResult;
using search::tensor::TensorAttribute;
using search::attribute::ArrayBoolAttribute;
using search::attribute::ReferenceAttribute;
using search::attribute::SingleRawAttribute;

namespace {
    std::string toString(const FieldUpdate & update) {
        std::stringstream ss;
        update.print(ss, true, "");
        return ss.str();
    }

    std::string toString(const ValueUpdate & update) {
        std::stringstream ss;
        update.print(ss, true, "");
        return ss.str();
    }

    std::string toString(const FieldValue & value) {
        std::stringstream ss;
        value.print(ss, true, "");
        return ss.str();
    }
}

namespace search {
    namespace forcelink {
        void forceLink()
        {
            document::ForceLink tmp;
        }
    }

struct GetFloat {
    using T = float;
    T operator () (const FieldValue & fv) const { return fv.getAsFloat(); }
};

struct GetDouble {
    using T = double;
    T operator () (const FieldValue & fv) const { return fv.getAsDouble(); }
};

struct GetLong {
    using T = int64_t;
    T operator () (const FieldValue & fv) const { return fv.getAsLong(); }
};

struct GetInt {
    using T = int32_t;
    T operator () (const FieldValue & fv) const { return fv.getAsInt(); }
};

struct GetString {
    using T = std::string_view;
    T operator () (const FieldValue & fv) const { return static_cast<const LiteralFieldValueB &>(fv).getValueRef(); }
};

VESPA_IMPLEMENT_EXCEPTION(UpdateException, vespalib::Exception);

template <typename G>
class ArrayAccessor {
public:
    ArrayAccessor(const ArrayFieldValue & array) : _array(array), _current(0), _size(_array.size()) { }
    size_t size()         const { return _size; }
    bool isAtEnd()        const { return _current >= _size;}
    void next()                 {  _current++; }
    typename G::T value() const { return _accessor(_array[_current]); }
    int32_t weight()      const { return 1; }
private:
    G _accessor;
    const ArrayFieldValue & _array;
    size_t _current;
    size_t _size;
};

template <typename G>
class WeightedSetAccessor {
public:
    WeightedSetAccessor(const WeightedSetFieldValue & ws) : _size(ws.size()), _current(ws.begin()), _end(ws.end()) { }
    size_t size()         const { return _size; }
    bool isAtEnd()        const { return _current == _end;}
    void next()                 { ++_current; }
    typename G::T value() const { return _accessor(*_current->first); }
    int32_t weight()      const { return _current->second->getAsInt(); }
private:
    G _accessor;
    size_t _size;
    MapFieldValue::const_iterator _current;
    MapFieldValue::const_iterator _end;
};

template <typename V, typename Accessor>
void
AttributeUpdater::handleUpdateT(V & vec, Accessor, uint32_t lid, const ValueUpdate & upd)
{
    LOG(spam, "handleValueUpdate(%s, %u): %s", vec.getName().c_str(), lid, toString(upd).c_str());
    ValueUpdate::ValueUpdateType op = upd.getType();
    if (vec.hasMultiValue()) {
        if (op == ValueUpdate::Clear) {
            vec.clearDoc(lid);
        } else if (op == ValueUpdate::Assign) {
            vec.clearDoc(lid);
            const AssignValueUpdate & assign(static_cast<const AssignValueUpdate &>(upd));
            if (assign.hasValue()) {
                const FieldValue & fv(assign.getValue());
                if (fv.isA(FieldValue::Type::ARRAY)) {
                    ArrayAccessor<Accessor> ac(static_cast<const ArrayFieldValue & >(fv));
                    appendValue(vec, lid, ac);
                } else if (fv.isA(FieldValue::Type::WSET)) {
                    WeightedSetAccessor<Accessor> ac(static_cast<const WeightedSetFieldValue & >(fv));
                    appendValue(vec, lid, ac);
                } else {
                    LOG(warning, "Unsupported value %s in assign operation on multivalue vector %s",
                                 fv.className(), vec.getName().c_str());
                }
            }
        } else if (op == ValueUpdate::Add) {
            const AddValueUpdate & add(static_cast<const AddValueUpdate &>(upd));
            appendValue(vec, lid, add.getValue(), add.getWeight());
        } else if (op == ValueUpdate::Remove) {
            const RemoveValueUpdate & remove(static_cast<const RemoveValueUpdate &>(upd));
            removeValue(vec, lid, remove.getKey());
        } else if (op == ValueUpdate::Map) {
            const MapValueUpdate & map(static_cast<const MapValueUpdate &>(upd));
            if (!vec.AttributeVector::apply(lid, map)) {
                throw UpdateException(make_string("attribute map(%s, %s) failed: %s[%u]",
                                                  map.getKey().className(), map.getUpdate().className(),
                                                  vec.getName().c_str(), lid));
            }
        } else {
            LOG(warning, "Unsupported value update operation %s on multivalue vector %s",
                         upd.className(), vec.getName().c_str());
        }
    } else {
        if (op == ValueUpdate::Assign) {
            const AssignValueUpdate & assign(static_cast<const AssignValueUpdate &>(upd));
            if (assign.hasValue()) {
                updateValue(vec, lid, assign.getValue());
            }
        } else if (op == ValueUpdate::Arithmetic) {
            const ArithmeticValueUpdate & aritmetic(static_cast<const ArithmeticValueUpdate &>(upd));
            if (!vec.apply(lid, aritmetic)) {
                throw UpdateException(make_string("attribute arithmetic failed: %s[%u]", vec.getName().c_str(), lid));
            }
        } else if (op == ValueUpdate::Clear) {
            vec.clearDoc(lid);
        } else {
            LOG(warning, "Unsupported value update operation %s on singlevalue vector %s", upd.className(), vec.getName().c_str());
        }
    }
}

template <>
void
AttributeUpdater::handleUpdate(PredicateAttribute &vec, uint32_t lid, const ValueUpdate &upd)
{
    LOG(spam, "handleValueUpdate(%s, %u): %s", vec.getName().c_str(), lid, toString(upd).c_str());
    ValueUpdate::ValueUpdateType op = upd.getType();
    assert(!vec.hasMultiValue());
    if (op == ValueUpdate::Assign) {
        const AssignValueUpdate &assign(static_cast<const AssignValueUpdate &>(upd));
        if (assign.hasValue()) {
            vec.clearDoc(lid);
            updateValue(vec, lid, assign.getValue());
        }
    } else if (op == ValueUpdate::Clear) {
        vec.clearDoc(lid);
    } else {
        LOG(warning, "Unsupported value update operation %s on singlevalue vector %s",
                     upd.className(), vec.getName().c_str());
    }
}

template <>
void
AttributeUpdater::handleUpdate(TensorAttribute &vec, uint32_t lid, const ValueUpdate &upd)
{
    LOG(spam, "handleUpdate(%s, %u): %s", vec.getName().c_str(), lid, toString(upd).c_str());
    ValueUpdate::ValueUpdateType op = upd.getType();
    assert(!vec.hasMultiValue());
    if (op == ValueUpdate::Assign) {
        const AssignValueUpdate &assign(static_cast<const AssignValueUpdate &>(upd));
        if (assign.hasValue()) {
            vec.clearDoc(lid);
            updateValue(vec, lid, assign.getValue());
        }
    } else if (op == ValueUpdate::TensorModify) {
        const auto& modify = static_cast<const TensorModifyUpdate&>(upd);
        vec.update_tensor(lid, modify, modify.get_default_cell_value().has_value());
    } else if (op == ValueUpdate::TensorAdd) {
        vec.update_tensor(lid, static_cast<const TensorAddUpdate &>(upd), true);
    } else if (op == ValueUpdate::TensorRemove) {
        vec.update_tensor(lid, static_cast<const TensorRemoveUpdate &>(upd), false);
    } else if (op == ValueUpdate::Clear) {
        vec.clearDoc(lid);
    } else {
        LOG(warning, "Unsupported value update operation %s on singlevalue tensor attribute %s",
                     upd.className(), vec.getName().c_str());
    }
}

template <>
void
AttributeUpdater::handleUpdate(ReferenceAttribute &vec, uint32_t lid, const ValueUpdate &upd)
{
    LOG(spam, "handleUpdate(%s, %u): %s", vec.getName().c_str(), lid, toString(upd).c_str());
    ValueUpdate::ValueUpdateType op = upd.getType();
    assert(!vec.hasMultiValue());
    if (op == ValueUpdate::Assign) {
        const AssignValueUpdate &assign(static_cast<const AssignValueUpdate &>(upd));
        if (assign.hasValue()) {
            updateValue(vec, lid, assign.getValue());
        }
    } else if (op == ValueUpdate::Clear) {
        vec.clearDoc(lid);
    } else {
        LOG(warning, "Unsupported value update operation %s on singlevalue reference attribute %s",
                     upd.className(), vec.getName().c_str());
    }
}

template <>
void
AttributeUpdater::handleUpdate(SingleRawAttribute& vec, uint32_t lid, const ValueUpdate& upd)
{
    LOG(spam, "handleUpdate(%s, %u): %s", vec.getName().c_str(), lid, toString(upd).c_str());
    ValueUpdate::ValueUpdateType op = upd.getType();
    assert(!vec.hasMultiValue());
    if (op == ValueUpdate::Assign) {
        const AssignValueUpdate &assign(static_cast<const AssignValueUpdate &>(upd));
        if (assign.hasValue()) {
            updateValue(vec, lid, assign.getValue());
        }
    } else if (op == ValueUpdate::Clear) {
        vec.clearDoc(lid);
    } else {
        LOG(warning, "Unsupported value update operation %s on singlevalue raw attribute %s",
            upd.className(), vec.getName().c_str());
    }
}

template <>
void
AttributeUpdater::handleUpdate(ArrayBoolAttribute &vec, uint32_t lid, const ValueUpdate &upd)
{
    LOG(spam, "handleUpdate(%s, %u): %s", vec.getName().c_str(), lid, toString(upd).c_str());
    ValueUpdate::ValueUpdateType op = upd.getType();
    if (op == ValueUpdate::Assign) {
        const AssignValueUpdate &assign(static_cast<const AssignValueUpdate &>(upd));
        if (assign.hasValue()) {
            updateValue(vec, lid, assign.getValue());
        } else {
            vec.clearDoc(lid);
        }
    } else if (op == ValueUpdate::Add) {
        const AddValueUpdate & add(static_cast<const AddValueUpdate &>(upd));
        appendValue(vec, lid, add.getValue());
    } else if (op == ValueUpdate::Clear) {
        vec.clearDoc(lid);
    } else {
        LOG(warning, "Unsupported value update operation %s on array bool attribute %s",
            upd.className(), vec.getName().c_str());
    }
}

void
AttributeUpdater::handleUpdate(AttributeVector & vec, uint32_t lid, const FieldUpdate & fUpdate)
{
    LOG(spam, "handleFieldUpdate(%s, %u): %s", vec.getName().c_str(), lid, toString(fUpdate).c_str());
    for(const auto & update : fUpdate.getUpdates()) {
        const ValueUpdate & vUp(*update);
        ValueUpdate::ValueUpdateType op = vUp.getType();

        if (!vec.hasMultiValue()) {
            if ((op == ValueUpdate::Add) ||
                (op == ValueUpdate::Remove) ||
                (op == ValueUpdate::Map))
            {
                LOG(warning, "operation append/remove not supported for "
                    "single value attribute vectors (%s)", vec.getName().c_str());
                continue;
            }
        }

        if (vec.isIntegerType()) {
            if (vec.getBasicType() == attribute::BasicType::BOOL && vec.hasMultiValue()) {
                handleUpdate(static_cast<ArrayBoolAttribute &>(vec), lid, vUp);
            } else {
                handleUpdateT(static_cast<IntegerAttribute &>(vec), GetLong(), lid, vUp);
            }
        } else if (vec.isFloatingPointType()) {
            handleUpdateT(static_cast<FloatingPointAttribute &>(vec), GetDouble(), lid, vUp);
        } else if (vec.isStringType()) {
            handleUpdateT(static_cast<StringAttribute &>(vec), GetString(), lid, vUp);
        } else if (vec.isPredicateType()) {
            handleUpdate(static_cast<PredicateAttribute &>(vec), lid, vUp);
        } else if (vec.isTensorType()) {
            handleUpdate(static_cast<TensorAttribute &>(vec), lid, vUp);
        } else if (vec.isReferenceType()) {
            handleUpdate(static_cast<ReferenceAttribute &>(vec), lid, vUp);
        } else if (vec.is_raw_type()) {
            handleUpdate(static_cast<SingleRawAttribute&>(vec), lid, vUp);
        } else {
            LOG(warning, "Unsupported attribute vector '%s' (classname=%s)", vec.getName().c_str(), getClassName(vec).c_str());
            return;
        }
    }
}

void
AttributeUpdater::handleValue(AttributeVector & vec, uint32_t lid, const FieldValue & val)
{
    LOG(spam, "handleValue(%s, %u): %s", vec.getName().c_str(), lid, toString(val).c_str());
    if (vec.isIntegerType()) {
        if (vec.getBasicType() == attribute::BasicType::BOOL && vec.hasMultiValue()) {
            updateValue(static_cast<ArrayBoolAttribute &>(vec), lid, val);
        } else {
            handleValueT(static_cast<IntegerAttribute &>(vec), GetLong(), lid, val);
        }
    } else if (vec.isFloatingPointType()) {
        handleValueT(static_cast<FloatingPointAttribute &>(vec), GetDouble(), lid, val);
    } else if (vec.isStringType()) {
        handleValueT(static_cast<StringAttribute &>(vec), GetString(), lid, val);
    } else if (vec.isPredicateType()) {
        // PredicateAttribute is never multivalue.
        updateValue(static_cast<PredicateAttribute &>(vec), lid, val);
    } else if (vec.isTensorType()) {
        // TensorAttribute is never multivalue.
        updateValue(static_cast<TensorAttribute &>(vec), lid, val);
    } else if (vec.isReferenceType()) {
        // ReferenceAttribute is never multivalue.
        updateValue(static_cast<ReferenceAttribute &>(vec), lid, val);
    } else if (vec.is_raw_type()) {
        // SingleRawAttribute is never multivalue
        updateValue(static_cast<SingleRawAttribute&>(vec), lid, val);
    } else {
        LOG(warning, "Unsupported attribute vector '%s' (classname=%s)", vec.getName().c_str(), getClassName(vec).c_str());
        return;
    }
}

template <typename V, typename Accessor>
void
AttributeUpdater::handleValueT(V & vec, Accessor, uint32_t lid, const FieldValue & val)
{
    if (vec.hasMultiValue()) {
        vec.clearDoc(lid);
        if (val.isA(FieldValue::Type::ARRAY)) {
            ArrayAccessor<Accessor> ac(static_cast<const ArrayFieldValue & >(val));
            appendValue(vec, lid, ac);
        } else if (val.isA(FieldValue::Type::WSET)) {
            WeightedSetAccessor<Accessor> ac(static_cast<const WeightedSetFieldValue & >(val));
            appendValue(vec, lid, ac);
        } else {
            LOG(warning, "Unsupported value '%s' to assign on multivalue vector '%s'", val.className(), vec.getName().c_str());
        }
    } else {
        updateValue(vec, lid, val);
    }
}

void
AttributeUpdater::appendValue(IntegerAttribute & vec, uint32_t lid, const FieldValue & val, int weight)
{
    int64_t v = val.getAsLong();
    if (!vec.append(lid, v, weight)) {
        throw UpdateException(make_string("attribute append failed: %s[%u] = %" PRIu64,
                                          vec.getName().c_str(), lid, v));
    }
}

void
AttributeUpdater::removeValue(IntegerAttribute & vec, uint32_t lid, const FieldValue & val)
{
    int64_t v = val.getAsLong();
    if (!vec.remove(lid, v, 1)) {
        throw UpdateException(make_string("attribute remove failed: %s[%u] = %" PRIu64,
                                          vec.getName().c_str(), lid, v));
    }
}

void
AttributeUpdater::updateValue(IntegerAttribute & vec, uint32_t lid, const FieldValue & val)
{
    int64_t v = val.getAsLong();
    if (!vec.update(lid, v)) {
        throw UpdateException(make_string("attribute update failed: %s[%u] = %" PRIu64,
                                          vec.getName().c_str(), lid, v));
    }
}

void
AttributeUpdater::appendValue(FloatingPointAttribute & vec, uint32_t lid, const FieldValue & val, int weight)
{
    double v = val.getAsDouble();
    if (!vec.append(lid, v, weight)) {
        throw UpdateException(make_string("attribute append failed: %s[%u] = %g",
                                          vec.getName().c_str(), lid, v));
    }
}

template <typename V, typename Accessor>
void
AttributeUpdater::appendValue(V & vec, uint32_t lid, Accessor & ac)
{
    if (!vec.append(lid, ac)) {
        throw UpdateException(make_string("attribute append failed: %s[%u]",
                                          vec.getName().c_str(), lid));
    }
}

void
AttributeUpdater::removeValue(FloatingPointAttribute & vec, uint32_t lid, const FieldValue & val)
{
    double v = val.getAsDouble();
    if (!vec.remove(lid, v, 1)) {
        throw UpdateException(make_string("attribute remove failed: %s[%u] = %g",
                                          vec.getName().c_str(), lid, v));
    }
}

void
AttributeUpdater::updateValue(FloatingPointAttribute & vec, uint32_t lid, const FieldValue & val)
{
    double v = val.getAsDouble();
    if (!vec.update(lid, v)) {
        throw UpdateException(make_string("attribute update failed: %s[%u] = %g",
                                          vec.getName().c_str(), lid, v));
    }
}

namespace {

const std::string &
getString(const search::StringAttribute & attr, uint32_t lid, const FieldValue & val) {
    if ( ! val.isLiteral() ) {
        throw UpdateException(make_string("Can not update a string attribute '%s' for lid=%d from a non-literal fieldvalue: %s",
                                          attr.getName().c_str(), lid, val.toString().c_str()));
    }
    return static_cast<const LiteralFieldValueB &>(val).getValue();
}

}

void
AttributeUpdater::appendValue(StringAttribute & vec, uint32_t lid, const FieldValue & val, int weight)
{
    const std::string & s = getString(vec, lid, val);
    if (!vec.append(lid, s, weight)) {
        throw UpdateException(make_string("attribute append failed: %s[%u] = %s",
                                          vec.getName().c_str(), lid, s.c_str()));
    }
}

void
AttributeUpdater::removeValue(StringAttribute & vec, uint32_t lid, const FieldValue & val)
{
    const std::string & v = getString(vec, lid, val);
    if (!vec.remove(lid, v, 1)) {
        throw UpdateException(make_string("attribute remove failed: %s[%u] = %s",
                                          vec.getName().c_str(), lid, v.c_str()));
    }
}

void
AttributeUpdater::updateValue(StringAttribute & vec, uint32_t lid, const FieldValue & val)
{
    const std::string & v = getString(vec, lid, val);
    if (!vec.update(lid, v)) {
        throw UpdateException(make_string("attribute update failed: %s[%u] = %s",
                                          vec.getName().c_str(), lid, v.c_str()));
    }
}

namespace {

void
validate_field_value_type(FieldValue::Type expectedType, const FieldValue& val, const std::string& attr_type, const std::string& value_type)
{
    if (!val.isA(expectedType)) {
        throw UpdateException(
                make_string("%s must be updated with %s, but was '%s'",
                            attr_type.c_str(), value_type.c_str(), val.toString(false).c_str()));
    }
}

}

void
AttributeUpdater::updateValue(PredicateAttribute &vec, uint32_t lid, const FieldValue &val)
{
    validate_field_value_type(FieldValue::Type::PREDICATE, val, "PredicateAttribute", "PredicateFieldValue");
    vec.updateValue(lid, static_cast<const PredicateFieldValue &>(val));
}

void
AttributeUpdater::updateValue(TensorAttribute &vec, uint32_t lid, const FieldValue &val)
{
    validate_field_value_type(FieldValue::Type::TENSOR, val, "TensorAttribute", "TensorFieldValue");
    const auto* tensor = static_cast<const TensorFieldValue &>(val).getAsTensorPtr();
    if (tensor) {
        vec.setTensor(lid, *tensor);
    } else {
        vec.clearDoc(lid);
    }
}

void
AttributeUpdater::updateValue(ReferenceAttribute &vec, uint32_t lid, const FieldValue &val)
{
    if (!val.isA(FieldValue::Type::REFERENCE)) {
        vec.clearDoc(lid);
        throw UpdateException(
                make_string("ReferenceAttribute must be updated with "
                            "ReferenceFieldValue, but was '%s'", val.toString(false).c_str()));
    }
    const auto &reffv = static_cast<const ReferenceFieldValue &>(val);
    if (reffv.hasValidDocumentId()) {
        vec.update(lid, reffv.getDocumentId().getGlobalId());
    } else {
        vec.clearDoc(lid);
    }
}

void
AttributeUpdater::updateValue(SingleRawAttribute& vec, uint32_t lid, const FieldValue& val)
{
    if (!val.isA(FieldValue::Type::RAW)) {
        vec.clearDoc(lid);
        throw UpdateException(
                make_string("SingleRawAttribute must be updated with "
                            "RawFieldValue, but was '%s'", val.toString(false).c_str()));
    }
    const auto& raw_fv = static_cast<const RawFieldValue &>(val);
    auto raw = raw_fv.getValueRef();
    vec.update(lid, {raw.data(), raw.size()});
}

void
AttributeUpdater::updateValue(ArrayBoolAttribute& vec, uint32_t lid, const FieldValue& val)
{
    if (!val.isA(FieldValue::Type::ARRAY)) {
        LOG(warning, "Unsupported value '%s' to assign on array bool attribute '%s'",
            val.className(), vec.getName().c_str());
        return;
    }
    const auto& array = static_cast<const ArrayFieldValue&>(val);
    std::vector<int8_t> bools;
    bools.reserve(array.size());
    for (uint32_t i = 0; i < array.size(); ++i) {
        bools.push_back(array[i].getAsLong() ? 1 : 0);
    }
    vec.set_bools(lid, bools);
}

void
AttributeUpdater::appendValue(ArrayBoolAttribute& vec, uint32_t lid, const FieldValue& val)
{
    std::vector<int8_t> bools;
    auto old_val = vec.get_bools(lid);
    bools.reserve(old_val.size() + 1);
    for (bool bit: old_val) {
        bools.push_back(bit ? 1 : 0);
    }
    bools.push_back(val.getAsLong() ? 1 : 0);
    vec.set_bools(lid, bools);
}

namespace {

void
validate_tensor_attribute_type(AttributeVector& attr)
{
    if (!attr.isTensorType()) {
        throw UpdateException(
                make_string("Expected attribute vector '%s' to be a TensorAttribute, but was '%s'",
                            attr.getName().c_str(), getClassName(attr).c_str()));
    }
}

std::unique_ptr<PrepareResult>
prepare_set_tensor(TensorAttribute& attr, uint32_t docid, const FieldValue& val)
{
    validate_field_value_type(FieldValue::Type::TENSOR, val, "TensorAttribute", "TensorFieldValue");
    const auto* tensor = static_cast<const TensorFieldValue&>(val).getAsTensorPtr();
    if (tensor) {
        return attr.prepare_set_tensor(docid, *tensor);
    }
    return std::unique_ptr<PrepareResult>();
}

void
complete_set_tensor(TensorAttribute& attr, uint32_t docid, const FieldValue& val, std::unique_ptr<PrepareResult> prepare_result)
{
    validate_field_value_type(FieldValue::Type::TENSOR, val, "TensorAttribute", "TensorFieldValue");
    const auto* tensor = static_cast<const TensorFieldValue&>(val).getAsTensorPtr();
    if (tensor) {
        attr.complete_set_tensor(docid, *tensor, std::move(prepare_result));
    } else {
        attr.clearDoc(docid);
    }
}

}

std::unique_ptr<PrepareResult>
AttributeUpdater::prepare_set_value(AttributeVector& attr, uint32_t docid, const FieldValue& val)
{
    validate_tensor_attribute_type(attr);
    return prepare_set_tensor(static_cast<TensorAttribute&>(attr), docid, val);
}

void
AttributeUpdater::complete_set_value(AttributeVector& attr, uint32_t docid, const FieldValue& val,
                                     std::unique_ptr<PrepareResult> prepare_result)
{
    validate_tensor_attribute_type(attr);
    complete_set_tensor(static_cast<TensorAttribute&>(attr), docid, val, std::move(prepare_result));
}

}  // namespace search
