// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "urldatatype.h"
#include <mutex>

namespace document {

namespace {

std::mutex   _G_lock;

}

StructDataType::UP UrlDataType::_instance;

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
    StructDataType::UP type(new StructDataType(UrlDataType::STRUCT_NAME));
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
    if ( ! _instance ) {
        std::lock_guard guard(_G_lock);
        if ( ! _instance ) {
            _instance = createInstance();
        }
    }
    return *_instance;
}

} // document
