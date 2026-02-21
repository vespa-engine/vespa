// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_field_retriever.h"
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/searchcommon/attribute/i_multi_value_attribute.h>
#include <vespa/searchlib/attribute/single_raw_attribute.h>
#include <vespa/searchlib/tensor/tensor_attribute.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.attribute.document_field_retriever");

using document::ArrayFieldValue;
using document::Document;
using document::Field;
using document::FieldValue;
using document::RawFieldValue;
using document::TensorFieldValue;
using document::WeightedSetFieldValue;
using search::DocumentIdT;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::IAttributeVector;
using search::attribute::IMultiValueAttribute;
using search::attribute::SingleRawAttribute;
using search::attribute::WeightedType;
using search::tensor::TensorAttribute;
using vespalib::IllegalStateException;
using vespalib::Stash;

namespace proton {

namespace {

template <typename WT, typename T, typename FT>
void
setValue(DocumentIdT lid, Document &doc, const document::Field & field, const IAttributeVector &attr);

template <typename WT, typename T, typename FT>
void
setValue(DocumentIdT lid, Document &doc, const document::Field & field, const IAttributeVector &attr)
{
    switch (attr.getCollectionType()) {
    case CollectionType::SINGLE:
    {
        if ( ! attr.isUndefined(lid) ) {
            if constexpr (std::is_same_v<IAttributeVector::largeint_t, WT>) {
                doc.setFieldValue(field, std::make_unique<FT>(attr.getInt(lid)));
            } else if constexpr (std::is_same_v<double, WT>) {
                doc.setFieldValue(field, std::make_unique<FT>(attr.getFloat(lid)));
            } else if constexpr (std::is_same_v<const char*, WT>) {
                auto value = attr.get_raw(lid);
                doc.setFieldValue(field, std::make_unique<FT>(std::string_view{value.data(), value.size()}));
            } else {
                static_assert(false, "unexpected data type");
            }
        } else {
            doc.remove(field);
        }
        break;
    }
    case CollectionType::ARRAY:
    {
        Stash stash;
        auto* mva = attr.as_multi_value_attribute();
        if (mva == nullptr) {
            doc.remove(field);
            break;
        }
        auto read_view = mva->make_read_view(IMultiValueAttribute::ArrayTag<T>(), stash);
        if (read_view == nullptr) {
            doc.remove(field);
            break;
        }
        auto values = read_view->get_values(lid);
        if (values.empty()) {
            doc.remove(field);
            break;
        }
        FieldValue::UP fv = field.getDataType().createFieldValue();
        if (fv && ! fv->isA(FieldValue::Type::ARRAY)) {
            throw IllegalStateException("Field " + field.getName() + " does not contain an array.", VESPA_STRLOC);
        }
        ArrayFieldValue &array = static_cast<ArrayFieldValue &>(*fv.get());
        array.resize(values.size());
        for (uint32_t j(0); j < values.size(); ++j) {
            static_cast<FT &>(array[j]).setValue(values[j]);
        }
        doc.setValue(field, *fv);
        break;
    }
    case CollectionType::WSET:
    {
        if constexpr (std::is_same_v<bool, T>) {
            doc.remove(field);
        } else {
            Stash stash;
            auto* mva = attr.as_multi_value_attribute();
            if (mva == nullptr) {
                doc.remove(field);
                break;
            }
            auto read_view = mva->make_read_view(IMultiValueAttribute::WeightedSetTag<T>(), stash);
            if (read_view == nullptr) {
                doc.remove(field);
                break;
            }
            auto values = read_view->get_values(lid);
            if (values.empty()) {
                doc.remove(field);
                break;
            }
            FieldValue::UP fv = field.getDataType().createFieldValue();
            if (fv &&  ! fv->isA(FieldValue::Type::WSET)) {
                throw IllegalStateException("Field " + field.getName() + " does not contain a wset.", VESPA_STRLOC);
            }
            WeightedSetFieldValue & wset(static_cast<WeightedSetFieldValue &>(*fv.get()));
            wset.resize(values.size());
            auto it(wset.begin());
            for (uint32_t j(0); j < values.size(); ++j, ++it) {
                static_cast<FT &>(*it->first).setValue(values[j].value());
                static_cast<document::IntFieldValue &>(*it->second).setValue(values[j].weight());
            }
            doc.setValue(field, *fv);
        }
        break;
    }
    default:
        LOG(warning, "Unknown attribute collection type in attribute.");
        break;
    }
}

void
set_raw_value(DocumentIdT lid, Document& doc, const document::Field& field,
              const IAttributeVector& attr)
{
    auto& raw_attr = static_cast<const SingleRawAttribute&>(attr);
    auto raw = raw_attr.get_raw(lid);
    if (raw.empty()) {
        doc.remove(field);
    } else {
        RawFieldValue raw_field(raw.data(), raw.size());
        doc.setValue(field, raw_field);
    }
}

void
setTensorValue(DocumentIdT lid, Document &doc,
               const document::Field &field,
               const IAttributeVector &attr)
{
    const auto &tensorAttribute = static_cast<const TensorAttribute &>(attr);
    auto tensor = tensorAttribute.getTensor(lid);
    if (tensor) {
        auto tensorField = field.createValue();
        dynamic_cast<TensorFieldValue &>(*tensorField) = std::move(tensor);
        doc.setValue(field, *tensorField);
    } else {
        doc.remove(field);
    }
}

}

void
DocumentFieldRetriever::populate(DocumentIdT lid,
                                 Document &doc,
                                 const document::Field & field,
                                 const IAttributeVector &attr)
{
    switch (attr.getBasicType()) {
    case BasicType::BOOL:
        return setValue<IAttributeVector::largeint_t, bool, document::BoolFieldValue>(lid, doc, field, attr);
    case BasicType::UINT2:
    case BasicType::UINT4:
    case BasicType::INT8:
        return setValue<IAttributeVector::largeint_t, int8_t, document::ByteFieldValue>(lid, doc, field, attr);
    case BasicType::INT16:
        return setValue<IAttributeVector::largeint_t, int16_t, document::ShortFieldValue>(lid, doc, field, attr);
    case BasicType::INT32:
        return setValue<IAttributeVector::largeint_t, int32_t, document::IntFieldValue>(lid, doc, field, attr);
    case BasicType::INT64:
        return setValue<IAttributeVector::largeint_t, int64_t, document::LongFieldValue>(lid, doc, field, attr);
    case BasicType::FLOAT:
        return setValue<double, float, document::FloatFieldValue>(lid, doc, field, attr);
    case BasicType::DOUBLE:
        return setValue<double, double, document::DoubleFieldValue>(lid, doc, field, attr);
    case BasicType::STRING:
        return setValue<const char *, const char *, document::StringFieldValue>(lid, doc, field, attr);
    case BasicType::RAW:
        return set_raw_value(lid, doc, field, attr);
    case BasicType::PREDICATE:
        // Predicate attribute doesn't store documents, it only indexes them.
        break;
    case BasicType::TENSOR:
        return setTensorValue(lid, doc, field, attr);
    case BasicType::REFERENCE:
        // Reference attribute doesn't store full document id.
        break;
    default:
        LOG(warning, "Unknown attribute data type in attribute.");
    }
}

} // namespace proton
