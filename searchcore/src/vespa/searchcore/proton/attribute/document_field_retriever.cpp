// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_field_retriever.h"
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchlib/tensor/tensor_attribute.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.attribute.document_field_retriever");

using document::ArrayFieldValue;
using document::Document;
using document::Field;
using document::FieldValue;
using document::TensorFieldValue;
using document::WeightedSetFieldValue;
using search::DocumentIdT;
using search::attribute::AttributeContent;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::IAttributeVector;
using search::attribute::WeightedType;
using search::tensor::TensorAttribute;
using vespalib::IllegalStateException;

namespace proton {

namespace {

template <typename T>
void
setValue(DocumentIdT lid,
         Document &doc,
         const document::Field & field,
         const IAttributeVector &attr)
{
    switch (attr.getCollectionType()) {
    case CollectionType::SINGLE:
    {
        if ( ! attr.isUndefined(lid) ) {
            AttributeContent<T> content;
            content.fill(attr, lid);
            doc.set(field, content[0]);
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
        if (fv.get() && fv->getClass().id() != ArrayFieldValue::classId) {
            throw IllegalStateException("Field " + field.getName() + " does not contain an array.", VESPA_STRLOC);
        }
        ArrayFieldValue &array = static_cast<ArrayFieldValue &>(*fv.get());
        array.resize(content.size());
        for (uint32_t j(0); j < content.size(); ++j) {
            array[j] = content[j];
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
        if (fv.get() && fv->getClass().id() != WeightedSetFieldValue::classId) {
            throw IllegalStateException("Field " + field.getName() + " does not contain a wset.", VESPA_STRLOC);
        }
        WeightedSetFieldValue & wset(static_cast<WeightedSetFieldValue &>(*fv.get()));
        wset.resize(content.size());
        auto it(wset.begin());
        for (uint32_t j(0); j < content.size(); ++j, ++it) {
            *it->first = content[j].getValue();
            *it->second = content[j].getWeight();
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
    case BasicType::UINT2:
    case BasicType::UINT4:
    case BasicType::INT8:
    case BasicType::INT16:
    case BasicType::INT32:
    case BasicType::INT64:
        setValue<IAttributeVector::largeint_t>(lid, doc, field, attr);
        break;
    case BasicType::FLOAT:
    case BasicType::DOUBLE:
        setValue<double>(lid, doc, field, attr);
        break;
    case BasicType::STRING:
        setValue<const char *>(lid, doc, field, attr);
        break;
    case BasicType::PREDICATE:
        // Predicate attribute doesn't store documents, it only indexes them.
        break;
    case BasicType::TENSOR:
        setTensorValue(lid, doc, field, attr);
        break;
    case BasicType::REFERENCE:
        // Reference attribute doesn't store full document id.
        break;
    default:
        LOG(warning, "Unknown attribute data type in attribute.");
    }
}

} // namespace proton
