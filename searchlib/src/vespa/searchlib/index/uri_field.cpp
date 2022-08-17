// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "uri_field.h"
#include <cassert>

namespace search::index {

UriField::UriField()
    : _all(Schema::UNKNOWN_FIELD_ID),
      _scheme(Schema::UNKNOWN_FIELD_ID),
      _host(Schema::UNKNOWN_FIELD_ID),
      _port(Schema::UNKNOWN_FIELD_ID),
      _path(Schema::UNKNOWN_FIELD_ID),
      _query(Schema::UNKNOWN_FIELD_ID),
      _fragment(Schema::UNKNOWN_FIELD_ID),
      _hostname(Schema::UNKNOWN_FIELD_ID)
{
}

bool
UriField::valid(const Schema &schema, uint32_t fieldId, const Schema::CollectionType &collectionType)
{
    if (fieldId == Schema::UNKNOWN_FIELD_ID) {
        return false;
    }
    const Schema::IndexField &field = schema.getIndexField(fieldId);
    if (field.getDataType() != schema::DataType::STRING) {
        return false;
    }
    if (field.getCollectionType() != collectionType) {
        return false;
    }
    return true;
}

bool
UriField::broken(const Schema &schema, const Schema::CollectionType & collectionType) const
{
    return !valid(schema, _all, collectionType) &&
        valid(schema, _scheme, collectionType) &&
        valid(schema, _host, collectionType) &&
        valid(schema, _port, collectionType) &&
        valid(schema, _path, collectionType) &&
        valid(schema, _query, collectionType) &&
        valid(schema, _fragment, collectionType);
}

bool
UriField::valid(const Schema &schema, const Schema::CollectionType & collectionType) const
{
    return valid(schema, _all, collectionType) &&
        valid(schema, _scheme, collectionType) &&
        valid(schema, _host, collectionType) &&
        valid(schema, _port, collectionType) &&
        valid(schema, _path, collectionType) &&
        valid(schema, _query, collectionType) &&
        valid(schema, _fragment, collectionType);
}

void
UriField::setup(const Schema &schema, const vespalib::string &field)
{
    _all = schema.getIndexFieldId(field);
    _scheme = schema.getIndexFieldId(field + ".scheme");
    _host = schema.getIndexFieldId(field + ".host");
    _port = schema.getIndexFieldId(field + ".port");
    _path = schema.getIndexFieldId(field + ".path");
    _query = schema.getIndexFieldId(field + ".query");
    _fragment = schema.getIndexFieldId(field + ".fragment");
    _hostname = schema.getIndexFieldId(field + ".hostname");
}

bool
UriField::mightBePartofUri(vespalib::stringref name) {
    size_t dotPos = name.find('.');
    if ((dotPos != 0) && (dotPos != vespalib::string::npos)) {
        vespalib::stringref suffix = name.substr(dotPos + 1);
        return ((suffix == "all") || (suffix == "scheme") || (suffix == "host") || (suffix == "port") ||
                (suffix == "path") || (suffix == "query") || (suffix == "fragment") || (suffix == "hostname"));
    }
    return false;
}

void
UriField::markUsed(UsedFieldsMap &usedFields, uint32_t field)
{
    if (field == Schema::UNKNOWN_FIELD_ID) {
        return;
    }
    assert(usedFields.size() > field);
    usedFields[field] = true;
}

void
UriField::markUsed(UsedFieldsMap &usedFields) const
{
    markUsed(usedFields, _all);
    markUsed(usedFields, _scheme);
    markUsed(usedFields, _host);
    markUsed(usedFields, _port);
    markUsed(usedFields, _path);
    markUsed(usedFields, _query);
    markUsed(usedFields, _fragment);
    markUsed(usedFields, _hostname);
}

}
