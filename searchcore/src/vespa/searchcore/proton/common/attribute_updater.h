// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/fieldvalue/fieldvalue.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/searchlib/attribute/attribute.h>
#include <vespa/vespalib/util/exception.h>

namespace search {

class PredicateAttribute;

namespace tensor {
    class PrepareResult;
    class TensorAttribute;
}
namespace attribute {
class ArrayBoolAttribute;
class ReferenceAttribute;
class SingleRawAttribute;
}

VESPA_DEFINE_EXCEPTION(UpdateException, vespalib::Exception);

/**
 * Class used to apply (document) field values and field updates to attribute vectors.
 */
class AttributeUpdater {
    using Field = document::Field;
    using FieldUpdate = document::FieldUpdate;
    using FieldValue = document::FieldValue;
    using ValueUpdate = document::ValueUpdate;

public:
    static void handleUpdate(AttributeVector & vec, uint32_t lid, const FieldUpdate & upd);
    static void handleValue(AttributeVector & vec, uint32_t lid, const FieldValue & val);

    static std::unique_ptr<tensor::PrepareResult> prepare_set_value(AttributeVector& attr, uint32_t docid, const FieldValue& val);
    static void complete_set_value(AttributeVector& attr, uint32_t docid, const FieldValue& val,
                                   std::unique_ptr<tensor::PrepareResult> prepare_result);

private:
    template <typename V>
    static void handleUpdate(V & vec, uint32_t lid, const ValueUpdate & upd);
    template <typename V, typename Accessor>
    static void handleValueT(V & vec, Accessor ac, uint32_t lid, const FieldValue & val);
    template <typename V, typename Accessor>
    static void handleUpdateT(V & vec, Accessor ac, uint32_t lid, const ValueUpdate & val);
    template <typename V, typename Accessor>
    static void appendValue(V & vec, uint32_t lid, Accessor & ac);
    static void appendValue(IntegerAttribute & vec, uint32_t lid, const FieldValue & val, int weight=1);
    static void removeValue(IntegerAttribute & vec, uint32_t lid, const FieldValue & val);
    static void updateValue(IntegerAttribute & vec, uint32_t lid, const FieldValue & val);
    static void appendValue(FloatingPointAttribute & vec, uint32_t lid, const FieldValue & val, int weight=1);
    static void removeValue(FloatingPointAttribute & vec, uint32_t lid, const FieldValue & val);
    static void updateValue(FloatingPointAttribute & vec, uint32_t lid, const FieldValue & val);
    static void appendValue(StringAttribute & vec, uint32_t lid, const FieldValue & val, int weight=1);
    static void removeValue(StringAttribute & vec, uint32_t lid, const FieldValue & val);
    static void updateValue(StringAttribute & vec, uint32_t lid, const FieldValue & val);
    static void updateValue(PredicateAttribute & vec, uint32_t lid, const FieldValue & val);
    static void updateValue(tensor::TensorAttribute & vec, uint32_t lid, const FieldValue & val);
    static void updateValue(attribute::ReferenceAttribute & vec, uint32_t lid, const FieldValue & val);
    static void updateValue(attribute::SingleRawAttribute& vec, uint32_t lid, const FieldValue& val);
    static void updateValue(attribute::ArrayBoolAttribute& vec, uint32_t lid, const FieldValue& val);
    static void appendValue(attribute::ArrayBoolAttribute& vec, uint32_t lid, const FieldValue& val);
};

}

