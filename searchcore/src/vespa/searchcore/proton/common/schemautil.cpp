// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "schemautil.h"

#include <vespa/log/log.h>
LOG_SETUP(".proton.common.schemautil");

using namespace search::index;

namespace proton {

namespace {

class FieldQuad
{
public:
    vespalib::string _name;
    vespalib::string _dataType;
    vespalib::string _collectionType;
    vespalib::string _location;

    FieldQuad(const vespalib::string &name,
              const vespalib::string &dataType,
              const vespalib::string &collectionType,
              const vespalib::string &location);
    ~FieldQuad();

    bool
    operator<(const FieldQuad &rhs) const
    {
        if (_name != rhs._name)
            return _name < rhs._name;
        if (_dataType != rhs._dataType)
            return _dataType < rhs._dataType;
        if (_collectionType != rhs._collectionType)
            return _collectionType < rhs._collectionType;
        return _location < rhs._location;
    }
};

FieldQuad::FieldQuad(const vespalib::string &name,
          const vespalib::string &dataType,
          const vespalib::string &collectionType,
          const vespalib::string &location)
        : _name(name),
          _dataType(dataType),
          _collectionType(collectionType),
          _location(location)
{
}
FieldQuad::~FieldQuad() {}

}


Schema::SP
SchemaUtil::makeHistorySchema(const Schema &newSchema,
                              const Schema &oldSchema,
                              const Schema &oldHistory)
{
    return makeHistorySchema(newSchema, oldSchema, oldHistory,
                             fastos::ClockSystem::now());
}

Schema::SP
SchemaUtil::makeHistorySchema(const Schema &newSchema,
                              const Schema &oldSchema,
                              const Schema &oldHistory,
                              int64_t now)
{
    Schema::SP history(new Schema);

    uint32_t fields = oldHistory.getNumIndexFields();
    for (uint32_t fieldId = 0; fieldId < fields; ++fieldId) {
        const Schema::IndexField &field = oldHistory.getIndexField(fieldId);
        uint32_t nFieldId = newSchema.getIndexFieldId(field.getName());
        if (nFieldId == Schema::UNKNOWN_FIELD_ID) {
            // Field not re-added, keep it in history
            history->addIndexField(field);
        }
    }
    fields = oldHistory.getNumAttributeFields();
    for (uint32_t fieldId = 0; fieldId < fields; ++fieldId) {
        const Schema::AttributeField &field =
            oldHistory.getAttributeField(fieldId);
        uint32_t nFieldId =
            newSchema.getAttributeFieldId(field.getName());
        if (nFieldId == Schema::UNKNOWN_FIELD_ID) {
            // Field not re-added, keep it in history
            history->addAttributeField(field);
        }
    }
    fields = oldHistory.getNumSummaryFields();
    for (uint32_t fieldId = 0; fieldId < fields; ++fieldId) {
        const Schema::SummaryField &field =
            oldHistory.getSummaryField(fieldId);
        uint32_t nFieldId =
            newSchema.getSummaryFieldId(field.getName());
        if (nFieldId == Schema::UNKNOWN_FIELD_ID) {
            // Field not re-added, keep it in history
            history->addSummaryField(field);
        }
    }
    fields = oldSchema.getNumIndexFields();
    for (uint32_t fieldId = 0; fieldId < fields; ++fieldId) {
        Schema::IndexField field = oldSchema.getIndexField(fieldId);
        uint32_t nFieldId = newSchema.getIndexFieldId(field.getName());
        if (nFieldId == Schema::UNKNOWN_FIELD_ID) {
            // Field removed, add to history
            uint32_t oFieldId = history->getIndexFieldId(field.getName());
            if (oFieldId == Schema::UNKNOWN_FIELD_ID) {
                field.setTimestamp(now);
                history->addIndexField(field);
            }
        }
    }
    fields = oldSchema.getNumAttributeFields();
    for (uint32_t fieldId = 0; fieldId < fields; ++fieldId) {
        Schema::AttributeField field = oldSchema.getAttributeField(fieldId);
        uint32_t nFieldId =
            newSchema.getAttributeFieldId(field.getName());
        if (nFieldId == Schema::UNKNOWN_FIELD_ID) {
            // Field removed, add to history
            uint32_t oFieldId = history->getAttributeFieldId(field.getName());
            if (oFieldId == Schema::UNKNOWN_FIELD_ID) {
                field.setTimestamp(now);
                history->addAttributeField(field);
            }
        }
    }
    fields = oldSchema.getNumSummaryFields();
    for (uint32_t fieldId = 0; fieldId < fields; ++fieldId) {
        Schema::SummaryField field = oldSchema.getSummaryField(fieldId);
        uint32_t nFieldId =
            newSchema.getSummaryFieldId(field.getName());
        if (nFieldId == Schema::UNKNOWN_FIELD_ID) {
            // Field removed, add to history
            uint32_t oFieldId = history->getSummaryFieldId(field.getName());
            if (oFieldId == Schema::UNKNOWN_FIELD_ID) {
                field.setTimestamp(now);
                history->addSummaryField(field);
            }
        }
    }
    return history;
}


Schema::SP
SchemaUtil::makeUnionSchema(const Schema &schema,
                            const Schema &history)
{
    Schema::SP uSchema(new Schema);
    *uSchema = schema;
    uint32_t fields = history.getNumIndexFields();
    for (uint32_t fieldId = 0; fieldId < fields; ++fieldId) {
        const Schema::IndexField &field = history.getIndexField(fieldId);
        uint32_t uFieldId = uSchema->getIndexFieldId(field.getName());
        if (uFieldId == Schema::UNKNOWN_FIELD_ID) {
            // Add back field known by history.
            uSchema->addIndexField(field);
        } else {
            LOG(error,
                "Index field '%s' is in both schema and history",
                field.getName().c_str());
        }
    }
    fields = history.getNumAttributeFields();
    for (uint32_t fieldId = 0; fieldId < fields; ++fieldId) {
        const Schema::AttributeField &field =
            history.getAttributeField(fieldId);
        uint32_t uFieldId =
            uSchema->getAttributeFieldId(field.getName());
        if (uFieldId == Schema::UNKNOWN_FIELD_ID) {
            // Add back field known by history.
            uSchema->addAttributeField(field);
        } else {
            LOG(error,
                "Attribute field '%s' is in both schema and history",
                field.getName().c_str());
        }
    }
    fields = history.getNumSummaryFields();
    for (uint32_t fieldId = 0; fieldId < fields; ++fieldId) {
        const Schema::SummaryField &field =
            history.getSummaryField(fieldId);
        uint32_t uFieldId =
            uSchema->getSummaryFieldId(field.getName());
        if (uFieldId == Schema::UNKNOWN_FIELD_ID) {
            // Add back field known by history.
            uSchema->addSummaryField(field);
        } else {
            LOG(error,
                "Summary field '%s' is in both schema and history",
                field.getName().c_str());
        }
    }
    return uSchema;
}


void
SchemaUtil::listSchema(const Schema &schema,
                       std::vector<vespalib::string> &fieldNames,
                       std::vector<vespalib::string> &fieldDataTypes,
                       std::vector<vespalib::string> &fieldCollectionTypes,
                       std::vector<vespalib::string> &fieldLocations)
{
    std::vector<FieldQuad> quads;
    for (uint32_t i = 0; i < schema.getNumAttributeFields(); ++i) {
        const Schema::AttributeField &field = schema.getAttributeField(i);
        quads.push_back(
                FieldQuad(field.getName(),
                          schema::getTypeName(field.getDataType()),
                          schema::getTypeName(field.getCollectionType()),
                          "a"));
    }
    for (uint32_t i = 0; i < schema.getNumIndexFields(); ++i) {
        const Schema::IndexField &field = schema.getIndexField(i);
        quads.push_back(
                FieldQuad(field.getName(),
                          schema::getTypeName(field.getDataType()),
                          schema::getTypeName(field.getCollectionType()),
                          "i"));
    }
    for (uint32_t i = 0; i < schema.getNumSummaryFields(); ++i) {
        const Schema::SummaryField &field = schema.getSummaryField(i);
        quads.push_back(
                FieldQuad(field.getName(),
                          schema::getTypeName(field.getDataType()),
                          schema::getTypeName(field.getCollectionType()),
                          "s"));
    }
    std::sort(quads.begin(), quads.end());
    std::string name;
    std::string dataType;
    std::string collectionType;
    std::string location;
    for (std::vector<FieldQuad>::const_iterator
             it = quads.begin(), ite = quads.end(); it != ite; ++it) {
        if (it->_name != name || it->_dataType != dataType ||
            it->_collectionType != collectionType) {
            if (!name.empty()) {
                fieldNames.push_back(name);
                fieldDataTypes.push_back(dataType);
                fieldCollectionTypes.push_back(collectionType);
                fieldLocations.push_back(location);
            }
            name = it->_name;
            dataType = it->_dataType;
            collectionType = it->_collectionType;
            location.clear();
        }
        location += it->_location;
    }
    if (!name.empty()) {
        fieldNames.push_back(name);
        fieldDataTypes.push_back(dataType);
        fieldCollectionTypes.push_back(collectionType);
        fieldLocations.push_back(location);
    }
}


} // namespace proton
