// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "schema_index_fields.h"

namespace search::index {

SchemaIndexFields::SchemaIndexFields()
    : _textFields(),
      _uriFields()
{
}

SchemaIndexFields::~SchemaIndexFields() = default;

void
SchemaIndexFields::setup(const Schema &schema)
{
    uint32_t numIndexFields = schema.getNumIndexFields();
    UriField::UsedFieldsMap usedFields;
    usedFields.resize(numIndexFields);

    // Detect all URI fields (flattened structs).
    for (uint32_t fieldId = 0; fieldId < numIndexFields; ++fieldId) {
        const Schema::IndexField &field = schema.getIndexField(fieldId);
        const vespalib::string &name = field.getName();
        size_t dotPos = name.find('.');
        if (dotPos != vespalib::string::npos) {
            const vespalib::string suffix = name.substr(dotPos + 1);
            if (suffix == "scheme") {
                const vespalib::string shortName = name.substr(0, dotPos);
                UriField uriField;
                uriField.setup(schema, shortName);
                if (uriField.valid(schema, field.getCollectionType())) {
                    _uriFields.push_back(uriField);
                    uriField.markUsed(usedFields);
                } else if (uriField.broken(schema, field.getCollectionType())) {
                    // Broken removal of unused URI fields.
                    uriField.markUsed(usedFields);
                }
            }
        }
    }

    // Non-URI fields are currently supposed to be text fields.
    for (uint32_t fieldId = 0; fieldId < numIndexFields; ++fieldId) {
        if (usedFields[fieldId]) {
            continue;
        }
        const Schema::IndexField &field = schema.getIndexField(fieldId);
        switch (field.getDataType()) {
        case schema::DataType::STRING:
            _textFields.push_back(fieldId);
            break;
        default:
            ;
        }
    }
}

}
