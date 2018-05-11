// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "schemautil.h"
#include <set>
#include <fstream>

#include <vespa/log/log.h>
LOG_SETUP(".index.schemautil");

namespace search::index {

using schema::DataType;

SchemaUtil::IndexSettings
SchemaUtil::getIndexSettings(const Schema &schema,
                             const uint32_t index)
{
    IndexSettings ret;
    Schema::DataType indexDataType(DataType::STRING);
    bool error = false;
    bool somePrefixes = false;
    bool someNotPrefixes = false;
    bool somePhrases = false;
    bool someNotPhrases = false;
    bool somePositions = false;
    bool someNotPositions = false;

    const Schema::IndexField &iField = schema.getIndexField(index);
    if (iField.hasPhrases())
        somePhrases = true;
    else
        someNotPhrases = true;
    if (iField.hasPrefix())
        somePrefixes = true;
    else
        someNotPrefixes = true;
    if (iField.hasPositions())
        somePositions = true;
    else
        someNotPositions = true;
    indexDataType = iField.getDataType();
    if (indexDataType != DataType::STRING) {
        error = true;
        LOG(error, "Field %s has bad data type", iField.getName().c_str());
    }

    return IndexSettings(indexDataType, error,
                         somePrefixes && !someNotPrefixes,
                         somePhrases && !someNotPhrases,
                         somePositions && !someNotPositions);
}


bool
SchemaUtil::IndexIterator::hasOldFields(const Schema &oldSchema,
                                        bool phrases) const
{
    assert(isValid());
    const Schema::IndexField &newField =
        getSchema().getIndexField(getIndex());
    const vespalib::string &fieldName = newField.getName();
    uint32_t oldFieldId = oldSchema.getIndexFieldId(fieldName);
    if (oldFieldId == Schema::UNKNOWN_FIELD_ID)
        return false;
    const Schema::IndexField &oldField =
        oldSchema.getIndexField(oldFieldId);
    if (oldField.getDataType() != newField.getDataType())
        return false;   // wrong data type
    if (!phrases)
        return true;
    return oldField.hasPhrases();
}


bool
SchemaUtil::IndexIterator::hasMatchingOldFields(const Schema &oldSchema,
        bool phrases) const
{
    assert(isValid());
    const Schema::IndexField &newField =
        getSchema().getIndexField(getIndex());
    const vespalib::string &fieldName = newField.getName();
    uint32_t oldFieldId = oldSchema.getIndexFieldId(fieldName);
    if (oldFieldId == Schema::UNKNOWN_FIELD_ID)
        return false;
    if (phrases) {
        IndexIterator oldIterator(oldSchema, oldFieldId);
        IndexSettings settings = oldIterator.getIndexSettings();
        if (!settings.hasPhrases())
            return false;
    }
    const Schema::IndexField &oldField =
        oldSchema.getIndexField(oldFieldId);
    if (oldField.getDataType() != newField.getDataType() ||
        oldField.getCollectionType() != newField.getCollectionType())
        return false;
    return true;
}


bool
SchemaUtil::validateIndexField(const Schema::IndexField &field)
{
    bool ok = true;
    if (!validateIndexFieldType(field.getDataType())) {
        LOG(error,
            "Field %s has bad data type",
            field.getName().c_str());
        ok = false;
    }
    if (field.getDataType() != DataType::STRING) {
        if (field.hasPrefix()) {
            LOG(error,
                "Field %s is non-string but has prefix",
                field.getName().c_str());
            ok = false;
        }
        if (field.hasPhrases()) {
            LOG(error,
                "Field %s is non-string but has phrases",
                field.getName().c_str());
            ok = false;
        }
        if (field.hasPositions()) {
            LOG(error,
                "Field %s is non-string but has positions",
                field.getName().c_str());
            ok = false;
        }
    }
    if (field.hasPhrases() && !field.hasPositions()) {
        LOG(error,
            "Field %s has phrases but not positions",
            field.getName().c_str());
        ok = false;
    }
    return ok;
}


bool
SchemaUtil::addIndexField(Schema &schema,
                          const Schema::IndexField &field)
{
    bool ok = true;
    if (!validateIndexField(field))
        ok = false;
    uint32_t fieldId = schema.getIndexFieldId(field.getName());
    if (fieldId != Schema::UNKNOWN_FIELD_ID) {
        LOG(error,
            "Field %s already exists in schema",
            field.getName().c_str());
        ok = false;
    }
    if (ok)
        schema.addIndexField(field);
    return ok;
}


bool
SchemaUtil::validateSchema(const Schema &schema)
{
    bool ok = true;
    for (IndexIterator it(schema); it.isValid(); ++it) {
        uint32_t fieldId = it.getIndex();
        const Schema::IndexField &field = schema.getIndexField(fieldId);
        if (!validateIndexField(field))
            ok = false;
        if (schema.getIndexFieldId(field.getName()) != fieldId) {
            LOG(error,
                "Duplcate field %s",
                field.getName().c_str());
            ok = false;
        }
    }
    for (uint32_t fsId = 0; fsId < schema.getNumFieldSets(); ++fsId) {
        const Schema::FieldSet &fs = schema.getFieldSet(fsId);
        if (schema.getFieldSetId(fs.getName()) != fsId) {
            LOG(error,
                "Duplicate field set %s",
                fs.getName().c_str());
            ok = false;
        }
    }
    return ok;
}


bool
SchemaUtil::getIndexIds(const Schema &schema,
                        schema::DataType dataType,
                        std::vector<uint32_t> &indexes)
{
    indexes.clear();
    for (IndexIterator i(schema); i.isValid(); ++i) {
        SchemaUtil::IndexSettings settings = i.getIndexSettings();
        if (settings.hasError())
            return false;
        if (settings.getDataType() == dataType)
            indexes.push_back(i.getIndex());
    }
    return true;
}

}
