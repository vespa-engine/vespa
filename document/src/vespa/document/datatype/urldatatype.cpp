// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "urldatatype.h"

namespace document {

const vespalib::string UrlDataType::STRUCT_NAME("url");
const vespalib::string UrlDataType::FIELD_ALL("all");
const vespalib::string UrlDataType::FIELD_SCHEME("scheme");
const vespalib::string UrlDataType::FIELD_HOST("host");
const vespalib::string UrlDataType::FIELD_PORT("port");
const vespalib::string UrlDataType::FIELD_PATH("path");
const vespalib::string UrlDataType::FIELD_QUERY("query");
const vespalib::string UrlDataType::FIELD_FRAGMENT("fragment");

StructDataType::UP
UrlDataType::createInstance()
{
    auto type = std::make_unique<StructDataType>(UrlDataType::STRUCT_NAME);
    type->addField(Field(UrlDataType::FIELD_ALL,     *DataType::STRING));
    type->addField(Field(UrlDataType::FIELD_SCHEME,  *DataType::STRING));
    type->addField(Field(UrlDataType::FIELD_HOST,    *DataType::STRING));
    type->addField(Field(UrlDataType::FIELD_PORT,    *DataType::STRING));
    type->addField(Field(UrlDataType::FIELD_PATH,    *DataType::STRING));
    type->addField(Field(UrlDataType::FIELD_QUERY,   *DataType::STRING));
    type->addField(Field(UrlDataType::FIELD_FRAGMENT,*DataType::STRING));
    return type;
}

const StructDataType &
UrlDataType::getInstance()
{
    static StructDataType::UP instance = createInstance();
    return *instance;
}

} // document
