// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/fieldvalue/fieldvalue.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/searchlib/attribute/attribute.h>
#include <vespa/searchlib/attribute/predicate_attribute.h>
#include <vespa/vespalib/util/exception.h>

namespace search {

using document::Field;
using document::FieldValue;
using document::FieldUpdate;
using document::ValueUpdate;
namespace tensor { class TensorAttribute; }
namespace attribute { class ReferenceAttribute; }

VESPA_DEFINE_EXCEPTION(UpdateException, vespalib::Exception);

class AttrUpdate {

public:
    static void handleUpdate(AttributeVector & vec, uint32_t lid, const FieldUpdate & upd);
    static void handleValue(AttributeVector & vec, uint32_t lid, const FieldValue & val);

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
};

}

