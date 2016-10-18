// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.attribute.document_field_retriever");
#include "document_field_retriever.h"

#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchlib/tensor/tensor_attribute.h>
#include <vespa/vespalib/tensor/tensor.h>

using search::DocumentIdT;
using document::ArrayFieldValue;
using document::Document;
using document::Field;
using document::FieldValue;
using document::TensorFieldValue;
using document::WeightedSetFieldValue;
using search::index::Schema;
using search::attribute::AttributeContent;
using search::attribute::IAttributeVector;
using search::attribute::WeightedType;
using search::attribute::TensorAttribute;
using vespalib::IllegalStateException;

namespace proton {

namespace {

template <typename T>
void
setValue(DocumentIdT lid,
         Document &doc,
         const Schema::AttributeField &field,
         const IAttributeVector &attr)
{
    switch (field.getCollectionType()) {
    case Schema::SINGLE:
    {
        if ( ! attr.isUndefined(lid) ) {
            AttributeContent<T> content;
            content.fill(attr, lid);
            doc.set(field.getName(), content[0]);
        } else {
            doc.remove(field.getName());
        }
        break;
    }
    case Schema::ARRAY:
    {
        AttributeContent<T> content;
        content.fill(attr, lid);
        Field f = doc.getField(field.getName());
        if (!doc.getValue(f) && content.size() == 0) {
            break;
        }
        FieldValue::UP fv = f.getDataType().createFieldValue();
        if (fv.get() && fv->getClass().id() != ArrayFieldValue::classId) {
            throw IllegalStateException("Field " + field.getName() + " does not contain an array.", VESPA_STRLOC);
        }
        ArrayFieldValue &array = static_cast<ArrayFieldValue &>(*fv.get());
        array.resize(content.size());
        for (uint32_t j(0); j < content.size(); ++j) {
            array[j] = content[j];
        }
        doc.setValue(f, *fv);
        break;
    }
    case Schema::WEIGHTEDSET:
    {
        AttributeContent<WeightedType<T> > content;
        content.fill(attr, lid);
        Field f = doc.getField(field.getName());
        if (!doc.getValue(f) && content.size() == 0) {
            break;
        }
        FieldValue::UP fv = f.getDataType().createFieldValue();
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
        doc.setValue(f, *fv);
        break;
    }
    default:
        LOG(warning, "Unknown attribute collection type in Schema.");
        break;
    }
}

}

void
DocumentFieldRetriever::populate(DocumentIdT lid,
                                 Document &doc,
                                 const Schema::AttributeField &field,
                                 const IAttributeVector &attr,
                                 bool isIndexField)
{
    switch (field.getDataType()) {
    case Schema::UINT1:
    case Schema::UINT2:
    case Schema::UINT4:
    case Schema::INT8:
    case Schema::INT16:
    case Schema::INT32:
    case Schema::INT64:
        setValue<IAttributeVector::largeint_t>(
                lid, doc, field, attr);
        break;
    case Schema::FLOAT:
    case Schema::DOUBLE:
        setValue<double>(lid, doc, field, attr);
        break;
    case Schema::STRING:
        // If it is a stringfield we also need to check if
        // it is an index field. In that case we shall
        // keep the original in order to preserve annotations.
        if (isIndexField) {
            break;
        }
    case Schema::RAW:
        setValue<const char *>(lid, doc, field, attr);
        break;
    case Schema::BOOLEANTREE:
        // Predicate attribute doesn't store documents, it only indexes them.
        break;
    case Schema::TENSOR:
        // Tensor attribute is not authorative.  Partial updates must update
        // document store.
        break;
    default:
        LOG(warning, "Unknown attribute data type in Schema.");
    }
}

} // namespace proton
