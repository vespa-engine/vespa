// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_updater.h"
#include <vespa/document/base/forcelink.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/literalfieldvalue.h>
#include <vespa/document/fieldvalue/predicatefieldvalue.h>
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
#include <vespa/searchlib/attribute/attributevector.hpp>
#include <vespa/searchlib/attribute/changevector.hpp>
#include <vespa/searchlib/attribute/predicate_attribute.h>
#include <vespa/searchlib/attribute/reference_attribute.h>
#include <vespa/searchlib/tensor/tensor_attribute.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".proton.common.attribute_updater");

using namespace document;
using vespalib::make_string;
using search::tensor::PrepareResult;
using search::tensor::TensorAttribute;
using search::attribute::ReferenceAttribute;

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
    typedef float T;
    T operator () (const FieldValue & fv) const { return fv.getAsFloat(); }
};

struct GetDouble {
    typedef double T;
    T operator () (const FieldValue & fv) const { return fv.getAsDouble(); }
};

struct GetLong {
    typedef int64_t T;
    T operator () (const FieldValue & fv) const { return fv.getAsLong(); }
};

struct GetInt {
    typedef int32_t T;
    T operator () (const FieldValue & fv) const { return fv.getAsInt(); }
};

struct GetString {
    typedef vespalib::stringref T;
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
                const vespalib::Identifiable::RuntimeClass & rc(fv.getClass());
                if (rc.inherits(ArrayFieldValue::classId)) {
                    ArrayAccessor<Accessor> ac(static_cast<const ArrayFieldValue & >(fv));
                    appendValue(vec, lid, ac);
                } else if (rc.inherits(WeightedSetFieldValue::classId)) {
                    WeightedSetAccessor<Accessor> ac(static_cast<const WeightedSetFieldValue & >(fv));
                    appendValue(vec, lid, ac);
                } else {
                    LOG(warning, "Unsupported value %s in assign operation on multivalue vector %s",
                                 rc.name(), vec.getName().c_str());
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
                                                  map.getKey().getClass().name(), map.getUpdate().getClass().name(),
                                                  vec.getName().c_str(), lid));
            }
        } else {
            LOG(warning, "Unsupported value update operation %s on multivalue vector %s",
                         upd.getClass().name(), vec.getName().c_str());
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
            LOG(warning, "Unsupported value update operation %s on singlevalue vector %s", upd.getClass().name(), vec.getName().c_str());
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
                     upd.getClass().name(), vec.getName().c_str());
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
    } else if (op == ValueUpdate::TensorModifyUpdate) {
        vec.update_tensor(lid, static_cast<const TensorModifyUpdate &>(upd), false);
    } else if (op == ValueUpdate::TensorAddUpdate) {
        vec.update_tensor(lid, static_cast<const TensorAddUpdate &>(upd), true);
    } else if (op == ValueUpdate::TensorRemoveUpdate) {
        vec.update_tensor(lid, static_cast<const TensorRemoveUpdate &>(upd), false);
    } else if (op == ValueUpdate::Clear) {
        vec.clearDoc(lid);
    } else {
        LOG(warning, "Unsupported value update operation %s on singlevalue tensor attribute %s",
                     upd.getClass().name(), vec.getName().c_str());
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
                     upd.getClass().name(), vec.getName().c_str());
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

        const vespalib::Identifiable::RuntimeClass &info = vec.getClass();
        if (info.inherits(IntegerAttribute::classId)) {
            handleUpdateT(static_cast<IntegerAttribute &>(vec), GetLong(), lid, vUp);
        } else if (info.inherits(FloatingPointAttribute::classId)) {
            handleUpdateT(static_cast<FloatingPointAttribute &>(vec), GetDouble(), lid, vUp);
        } else if (info.inherits(StringAttribute::classId)) {
            handleUpdateT(static_cast<StringAttribute &>(vec), GetString(), lid, vUp);
        } else if (info.inherits(PredicateAttribute::classId)) {
            handleUpdate(static_cast<PredicateAttribute &>(vec), lid, vUp);
        } else if (info.inherits(TensorAttribute::classId)) {
            handleUpdate(static_cast<TensorAttribute &>(vec), lid, vUp);
        } else if (info.inherits(ReferenceAttribute::classId)) {
            handleUpdate(static_cast<ReferenceAttribute &>(vec), lid, vUp);
        } else {
            LOG(warning, "Unsupported attribute vector '%s' (classname=%s)", vec.getName().c_str(), info.name());
            return;
        }
    }
}

void
AttributeUpdater::handleValue(AttributeVector & vec, uint32_t lid, const FieldValue & val)
{
    LOG(spam, "handleValue(%s, %u): %s", vec.getName().c_str(), lid, toString(val).c_str());
    const vespalib::Identifiable::RuntimeClass & rc = vec.getClass();
    if (rc.inherits(IntegerAttribute::classId)) {
        handleValueT(static_cast<IntegerAttribute &>(vec), GetLong(), lid, val);
    } else if (rc.inherits(FloatingPointAttribute::classId)) {
        handleValueT(static_cast<FloatingPointAttribute &>(vec), GetDouble(), lid, val);
    } else if (rc.inherits(StringAttribute::classId)) {
        handleValueT(static_cast<StringAttribute &>(vec), GetString(), lid, val);
    } else if (rc.inherits(PredicateAttribute::classId)) {
        // PredicateAttribute is never multivalue.
        updateValue(static_cast<PredicateAttribute &>(vec), lid, val);
    } else if (rc.inherits(TensorAttribute::classId)) {
        // TensorAttribute is never multivalue.
        updateValue(static_cast<TensorAttribute &>(vec), lid, val);
    } else if (rc.inherits(ReferenceAttribute::classId)) {
        // ReferenceAttribute is never multivalue.
        updateValue(static_cast<ReferenceAttribute &>(vec), lid, val);
    } else {
        LOG(warning, "Unsupported attribute vector '%s' (classname=%s)", vec.getName().c_str(), rc.name());
        return;
    }
}

template <typename V, typename Accessor>
void
AttributeUpdater::handleValueT(V & vec, Accessor, uint32_t lid, const FieldValue & val)
{
    if (vec.hasMultiValue()) {
        vec.clearDoc(lid);
        const vespalib::Identifiable::RuntimeClass & rc = val.getClass();
        if (rc.inherits(ArrayFieldValue::classId)) {
            ArrayAccessor<Accessor> ac(static_cast<const ArrayFieldValue & >(val));
            appendValue(vec, lid, ac);
        } else if (rc.inherits(WeightedSetFieldValue::classId)) {
            WeightedSetAccessor<Accessor> ac(static_cast<const WeightedSetFieldValue & >(val));
            appendValue(vec, lid, ac);
        } else {
            LOG(warning, "Unsupported value '%s' to assign on multivalue vector '%s'", rc.name(), vec.getName().c_str());
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

const vespalib::string &
getString(const search::StringAttribute & attr, uint32_t lid, const FieldValue & val) {
    if ( ! val.inherits(LiteralFieldValueB::classId) ) {
        throw UpdateException(make_string("Can not update a string attribute '%s' for lid=%d from a non-literal fieldvalue: %s",
                                          attr.getName().c_str(), lid, val.toString().c_str()));
    }
    return static_cast<const LiteralFieldValueB &>(val).getValue();
}

}

void
AttributeUpdater::appendValue(StringAttribute & vec, uint32_t lid, const FieldValue & val, int weight)
{
    const vespalib::string & s = getString(vec, lid, val);
    if (!vec.append(lid, s, weight)) {
        throw UpdateException(make_string("attribute append failed: %s[%u] = %s",
                                          vec.getName().c_str(), lid, s.c_str()));
    }
}

void
AttributeUpdater::removeValue(StringAttribute & vec, uint32_t lid, const FieldValue & val)
{
    const vespalib::string & v = getString(vec, lid, val);
    if (!vec.remove(lid, v, 1)) {
        throw UpdateException(make_string("attribute remove failed: %s[%u] = %s",
                                          vec.getName().c_str(), lid, v.c_str()));
    }
}

void
AttributeUpdater::updateValue(StringAttribute & vec, uint32_t lid, const FieldValue & val)
{
    const vespalib::string & v = getString(vec, lid, val);
    if (!vec.update(lid, v)) {
        throw UpdateException(make_string("attribute update failed: %s[%u] = %s",
                                          vec.getName().c_str(), lid, v.c_str()));
    }
}

namespace {

template <typename ExpFieldValueType>
void
validate_field_value_type(const FieldValue& val, const vespalib::string& attr_type, const vespalib::string& value_type)
{
    if (!val.inherits(ExpFieldValueType::classId)) {
        throw UpdateException(
                make_string("%s must be updated with %s, but was '%s'",
                            attr_type.c_str(), value_type.c_str(), val.toString(false).c_str()));
    }
}

}

void
AttributeUpdater::updateValue(PredicateAttribute &vec, uint32_t lid, const FieldValue &val)
{
    validate_field_value_type<PredicateFieldValue>(val, "PredicateAttribute", "PredicateFieldValue");
    vec.updateValue(lid, static_cast<const PredicateFieldValue &>(val));
}

void
AttributeUpdater::updateValue(TensorAttribute &vec, uint32_t lid, const FieldValue &val)
{
    validate_field_value_type<TensorFieldValue>(val, "TensorAttribute", "TensorFieldValue");
    const auto &tensor = static_cast<const TensorFieldValue &>(val).getAsTensorPtr();
    if (tensor) {
        vec.setTensor(lid, *tensor);
    } else {
        vec.clearDoc(lid);
    }
}

void
AttributeUpdater::updateValue(ReferenceAttribute &vec, uint32_t lid, const FieldValue &val)
{
    if (!val.inherits(ReferenceFieldValue::classId)) {
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

namespace {

void
validate_tensor_attribute_type(AttributeVector& attr)
{
    const auto& info = attr.getClass();
    if (!info.inherits(TensorAttribute::classId)) {
        throw UpdateException(
                make_string("Expected attribute vector '%s' to be a TensorAttribute, but was '%s'",
                            attr.getName().c_str(), info.name()));
    }
}

std::unique_ptr<PrepareResult>
prepare_set_tensor(TensorAttribute& attr, uint32_t docid, const FieldValue& val)
{
    validate_field_value_type<TensorFieldValue>(val, "TensorAttribute", "TensorFieldValue");
    const auto& tensor = static_cast<const TensorFieldValue&>(val).getAsTensorPtr();
    if (tensor) {
        return attr.prepare_set_tensor(docid, *tensor);
    }
    return std::unique_ptr<PrepareResult>();
}

void
complete_set_tensor(TensorAttribute& attr, uint32_t docid, const FieldValue& val, std::unique_ptr<PrepareResult> prepare_result)
{
    validate_field_value_type<TensorFieldValue>(val, "TensorAttribute", "TensorFieldValue");
    const auto& tensor = static_cast<const TensorFieldValue&>(val).getAsTensorPtr();
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
