// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_field_retriever.h"
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
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
using search::attribute::AttributeContent;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::IAttributeVector;
using search::attribute::SingleRawAttribute;
using search::attribute::WeightedType;
using search::tensor::TensorAttribute;
using vespalib::IllegalStateException;

namespace proton {

namespace {

template <typename T, typename FT>
void
setValue(DocumentIdT lid, Document &doc, const document::Field & field, const IAttributeVector &attr);

template <typename T, typename FT>
void
setValue(DocumentIdT lid, Document &doc, const document::Field & field, const IAttributeVector &attr)
{
    switch (attr.getCollectionType()) {
    case CollectionType::SINGLE:
    {
        if ( ! attr.isUndefined(lid) ) {
            AttributeContent<T> content;
            content.fill(attr, lid);
            doc.setFieldValue(field, std::make_unique<FT>(content[0]));
        } else {
            doc.remove(field);
        }
        break;
    }
    case CollectionType::ARRAY:
    {
        AttributeContent<T> content;
        content.fill(attr, lid);
        if (content.size() == 0) {
            doc.remove(field);
            break;
        }
        FieldValue::UP fv = field.getDataType().createFieldValue();
        if (fv && ! fv->isA(FieldValue::Type::ARRAY)) {
            throw IllegalStateException("Field " + field.getName() + " does not contain an array.", VESPA_STRLOC);
        }
        ArrayFieldValue &array = static_cast<ArrayFieldValue &>(*fv.get());
        array.resize(content.size());
        for (uint32_t j(0); j < content.size(); ++j) {
            static_cast<FT &>(array[j]).setValue(content[j]);
        }
        doc.setValue(field, *fv);
        break;
    }
    case CollectionType::WSET:
    {
        AttributeContent<WeightedType<T> > content;
        content.fill(attr, lid);
        if (content.size() == 0) {
            doc.remove(field);
            break;
        }
        FieldValue::UP fv = field.getDataType().createFieldValue();
        if (fv &&  ! fv->isA(FieldValue::Type::WSET)) {
            throw IllegalStateException("Field " + field.getName() + " does not contain a wset.", VESPA_STRLOC);
        }
        WeightedSetFieldValue & wset(static_cast<WeightedSetFieldValue &>(*fv.get()));
        wset.resize(content.size());
        auto it(wset.begin());
        for (uint32_t j(0); j < content.size(); ++j, ++it) {
            static_cast<FT &>(*it->first).setValue(content[j].getValue());
            static_cast<document::IntFieldValue &>(*it->second).setValue(content[j].getWeight());
        }
        doc.setValue(field, *fv);
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
        return setValue<IAttributeVector::largeint_t, document::BoolFieldValue>(lid, doc, field, attr);
    case BasicType::UINT2:
    case BasicType::UINT4:
    case BasicType::INT8:
        return setValue<IAttributeVector::largeint_t, document::ByteFieldValue>(lid, doc, field, attr);
    case BasicType::INT16:
        return setValue<IAttributeVector::largeint_t, document::ShortFieldValue>(lid, doc, field, attr);
    case BasicType::INT32:
        return setValue<IAttributeVector::largeint_t, document::IntFieldValue>(lid, doc, field, attr);
    case BasicType::INT64:
        return setValue<IAttributeVector::largeint_t, document::LongFieldValue>(lid, doc, field, attr);
    case BasicType::FLOAT:
        return setValue<double, document::FloatFieldValue>(lid, doc, field, attr);
    case BasicType::DOUBLE:
        return setValue<double, document::DoubleFieldValue>(lid, doc, field, attr);
    case BasicType::STRING:
        return setValue<const char *, document::StringFieldValue>(lid, doc, field, attr);
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
