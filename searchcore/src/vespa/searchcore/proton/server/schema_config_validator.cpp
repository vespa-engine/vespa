// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.server.schema_config_validator");
#include "schema_config_validator.h"
#include <vespa/vespalib/util/stringfmt.h>

using search::index::Schema;
using vespalib::make_string;

namespace proton {

namespace {

const vespalib::string INDEX_TYPE_NAME = "index";
const vespalib::string ATTRIBUTE_TYPE_NAME = "attribute";
const vespalib::string SUMMARY_TYPE_NAME = "summary";

typedef ConfigValidator CV;

struct SchemaSpec
{
    const Schema &_newSchema;
    const Schema &_oldSchema;
    const Schema &_oldHistory;
    SchemaSpec(const Schema &newSchema,
               const Schema &oldSchema,
               const Schema &oldHistory)
        : _newSchema(newSchema),
          _oldSchema(oldSchema),
          _oldHistory(oldHistory)
    {
    }
};

struct IndexChecker {
    static vespalib::string TypeName;
    static CV::ResultType AspectAdded;
    static CV::ResultType AspectRemoved;

    static bool
    inSchema(const vespalib::string &name,
             const Schema &schema,
             const Schema &hSchema)
    {
        return (schema.isIndexField(name) || hSchema.isIndexField(name)) &&
            (schema.isAttributeField(name) || schema.isSummaryField(name) ||
             hSchema.isAttributeField(name) || hSchema.isSummaryField(name));
    }
    static bool
    notInSchema(const vespalib::string &name,
                const Schema &schema,
                const Schema &hSchema)
    {
        return !schema.isIndexField(name) &&
            (schema.isAttributeField(name) || schema.isSummaryField(name) ||
             hSchema.isAttributeField(name) || hSchema.isSummaryField(name));
    }
};
vespalib::string IndexChecker::TypeName = INDEX_TYPE_NAME;
CV::ResultType IndexChecker::AspectAdded = CV::INDEX_ASPECT_ADDED;
CV::ResultType IndexChecker::AspectRemoved = CV::INDEX_ASPECT_REMOVED;

struct AttributeChecker {
    static vespalib::string TypeName;
    static CV::ResultType AspectAdded;
    static CV::ResultType AspectRemoved;

    static bool
    inSchema(const vespalib::string &name,
             const Schema &schema,
             const Schema &hSchema)
    {
        return (schema.isAttributeField(name) ||
                hSchema.isAttributeField(name)) &&
            (schema.isSummaryField(name) ||
             hSchema.isSummaryField(name));
    }
    static bool
    notInSchema(const vespalib::string &name,
                const Schema &schema,
                const Schema &hSchema)
    {
        return !schema.isAttributeField(name) &&
            (schema.isIndexField(name) || schema.isSummaryField(name) ||
             hSchema.isIndexField(name) || hSchema.isSummaryField(name));
    }
};
vespalib::string AttributeChecker::TypeName = ATTRIBUTE_TYPE_NAME;
CV::ResultType AttributeChecker::AspectAdded = CV::ATTRIBUTE_ASPECT_ADDED;
CV::ResultType AttributeChecker::AspectRemoved = CV::ATTRIBUTE_ASPECT_REMOVED;

bool
unchangedAspects(const vespalib::string &fieldName,
                 const Schema &newSchema,
                 const Schema &oldSchema,
                 const Schema &oldHistory)
{
    if (oldSchema.isIndexField(fieldName) ||
        oldSchema.isAttributeField(fieldName) ||
        oldSchema.isSummaryField(fieldName))
        return false; // field not removed
    return (newSchema.isIndexField(fieldName) ==
            oldHistory.isIndexField(fieldName) &&
            newSchema.isAttributeField(fieldName) ==
            oldHistory.isAttributeField(fieldName) &&
            newSchema.isSummaryField(fieldName) ==
            oldHistory.isSummaryField(fieldName));
}

template <typename Checker>
CV::Result
checkAspectAdded(const Schema::Field &field,
                 const SchemaSpec &spec)
{
    // Special check for undo scenarios.
    if (unchangedAspects(field.getName(), spec._newSchema, spec._oldSchema, spec._oldHistory)) {
        return CV::Result();
    }
    if (Checker::notInSchema(field.getName(), spec._oldSchema, spec._oldHistory)) {
        return CV::Result(Checker::AspectAdded,
                make_string("Trying to add %s field `%s', but it has existed as a field before",
                        Checker::TypeName.c_str(), field.getName().c_str()));
    }
    return CV::Result();
}

template <typename Checker>
CV::Result
checkAspectRemoved(const Schema::Field &field,
                   const SchemaSpec &spec)
{
    // Special check for undo scenarios.
    if (unchangedAspects(field.getName(), spec._newSchema, spec._oldSchema, spec._oldHistory)) {
        return CV::Result();
    }
    if (Checker::inSchema(field.getName(), spec._oldSchema, spec._oldHistory)) {
        return CV::Result(Checker::AspectRemoved,
                make_string("Trying to remove %s field `%s', but it still exists as a field",
                        Checker::TypeName.c_str(), field.getName().c_str()));
    }
    return CV::Result();
}

struct IndexTraits
{
    static vespalib::string TypeName;
    static uint32_t getFieldId(const vespalib::string &name, const Schema &schema) {
        return schema.getIndexFieldId(name);
    }
    static const Schema::Field &getField(uint32_t fieldId, const Schema &schema) {
        return schema.getIndexField(fieldId);
    }
};
vespalib::string IndexTraits::TypeName = INDEX_TYPE_NAME;

struct AttributeTraits
{
    static vespalib::string TypeName;
    static uint32_t getFieldId(const vespalib::string &name, const Schema &schema) {
        return schema.getAttributeFieldId(name);
    }
    static const Schema::Field &getField(uint32_t fieldId, const Schema &schema) {
        return schema.getAttributeField(fieldId);
    }
};
vespalib::string AttributeTraits::TypeName = ATTRIBUTE_TYPE_NAME;

struct SummaryTraits
{
    static vespalib::string TypeName;
    static uint32_t getFieldId(const vespalib::string &name, const Schema &schema) {
        return schema.getSummaryFieldId(name);
    }
    static const Schema::Field &getField(uint32_t fieldId, const Schema &schema) {
        return schema.getSummaryField(fieldId);
    }
};
vespalib::string SummaryTraits::TypeName = SUMMARY_TYPE_NAME;

CV::Result
checkDataTypeFunc(const Schema::Field &oldField,
                  const Schema::Field &newField,
                  const vespalib::string &fieldClass)
{
    if (oldField.getDataType() != newField.getDataType()) {
        return CV::Result(CV::DATA_TYPE_CHANGED,
                make_string("Trying to add %s field `%s' of data type %s, "
                        "but it has been of of data type %s earlier",
                        fieldClass.c_str(), newField.getName().c_str(),
                        Schema::getTypeName(newField.getDataType()).c_str(),
                        Schema::getTypeName(oldField.getDataType()).c_str()));
    }
    return CV::Result();
}

CV::Result
checkCollectionTypeFunc(const Schema::Field &oldField,
                        const Schema::Field &newField,
                        const vespalib::string &fieldClass)
{
    if (oldField.getCollectionType() != newField.getCollectionType()) {
        return CV::Result(CV::COLLECTION_TYPE_CHANGED,
                make_string("Trying to add %s field `%s' of collection type %s, "
                        "but it has been of of collection type %s earlier",
                        fieldClass.c_str(), newField.getName().c_str(),
                        Schema::getTypeName(newField.getCollectionType()).c_str(),
                        Schema::getTypeName(oldField.getCollectionType()).c_str()));
    }
    return CV::Result();
}

template <typename T, typename CheckFunc>
CV::Result
checkType(const Schema::Field &field, const Schema &oldSchema, CheckFunc func)
{
    uint32_t oFieldId = T::getFieldId(field.getName(), oldSchema);
    if (oFieldId != Schema::UNKNOWN_FIELD_ID) {
        const Schema::Field &oField = T::getField(oFieldId, oldSchema);
        CV::Result res = func(oField, field, T::TypeName);
        if (!res.ok()) {
            return res;
        }
    }
    return CV::Result();
}

template <typename T, typename CheckFunc>
CV::Result
checkType(const Schema::Field &field, const SchemaSpec &spec, CheckFunc func)
{
    CV::Result res;
    if (!(res = checkType<T>(field, spec._oldSchema, func)).ok()) return res;
    if (!(res = checkType<T>(field, spec._oldHistory, func)).ok()) return res;
    return CV::Result();
}

template <typename CheckFunc>
CV::Result
checkType(const SchemaSpec &spec, CheckFunc func)
{
    CV::Result res;
    for (const auto &f : spec._newSchema.getIndexFields()) {
        if (!(res = checkType<IndexTraits>(f, spec, func)).ok()) return res;
    }
    for (const auto &f : spec._newSchema.getAttributeFields()) {
        if (!(res = checkType<AttributeTraits>(f, spec, func)).ok()) return res;
    }
    for (const auto &f : spec._newSchema.getSummaryFields()) {
        if (!(res = checkType<SummaryTraits>(f, spec, func)).ok()) return res;
    }
    return CV::Result();
}

CV::Result
checkDataType(const SchemaSpec &spec)
{
    return checkType(spec, checkDataTypeFunc);
}

CV::Result
checkCollectionType(const SchemaSpec &spec)
{
    return checkType(spec, checkCollectionTypeFunc);
}

CV::Result
checkIndexAspectAdded(const SchemaSpec &spec)
{
    CV::Result res;
    for (const auto &f : spec._newSchema.getIndexFields()) {
        if (!(res = checkAspectAdded<IndexChecker>(f, spec)).ok()) return res;
    }
    return CV::Result();
}

CV::Result
checkIndexAspectRemoved(const SchemaSpec &spec)
{
    CV::Result res;
    for (const auto &f : spec._newSchema.getAttributeFields()) {
        if (!spec._newSchema.isIndexField(f.getName())) {
            if (!(res = checkAspectRemoved<IndexChecker>(f, spec)).ok()) return res;
        }
    }
    for (const auto &f : spec._newSchema.getSummaryFields()) {
        if (!spec._newSchema.isIndexField(f.getName())) {
            if (!(res = checkAspectRemoved<IndexChecker>(f, spec)).ok()) return res;
        }
    }
    return CV::Result();
}

CV::Result
checkAttributeAspectAdded(const SchemaSpec &spec)
{
    CV::Result res;
    for (const auto &f : spec._newSchema.getAttributeFields()) {
        if (!(res = checkAspectAdded<AttributeChecker>(f, spec)).ok()) return res;
    }
    return CV::Result();
}

CV::Result
checkAttributeAspectRemoved(const SchemaSpec &spec)
{
    CV::Result res;
    // Note: remove as attribute is allowed when still existing as index
    // so no need to iterator all index fields.

    for (const auto &f : spec._newSchema.getSummaryFields()) {
        if (!spec._newSchema.isAttributeField(f.getName()) &&
                !spec._newSchema.isIndexField(f.getName()) &&
                !spec._oldSchema.isIndexField(f.getName()))
        {
            if (!(res = checkAspectRemoved<AttributeChecker>(f, spec)).ok()) return res;
        }
    }
    return CV::Result();
}

}

CV::Result
SchemaConfigValidator::validate(const Schema &newSchema,
                                const Schema &oldSchema,
                                const Schema &oldHistory)
{
    LOG(debug, "validate(): newSchema='%s', oldSchema='%s', oldHistory='%s'",
        newSchema.toString().c_str(), oldSchema.toString().c_str(), oldHistory.toString().c_str());
    CV::Result res;
    SchemaSpec spec(newSchema, oldSchema, oldHistory);
    if (!(res = checkDataType(spec)).ok()) return res;
    if (!(res = checkCollectionType(spec)).ok()) return res;
    if (!(res = checkIndexAspectAdded(spec)).ok()) return res;
    if (!(res = checkIndexAspectRemoved(spec)).ok()) return res;
    if (!(res = checkAttributeAspectRemoved(spec)).ok()) return res;
    if (!(res = checkAttributeAspectAdded(spec)).ok()) return res;
    return CV::Result();
}

} // namespace proton
