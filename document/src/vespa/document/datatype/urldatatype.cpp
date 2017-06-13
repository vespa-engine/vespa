// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "urldatatype.h"

namespace document {

StructDataType::UP UrlDataType::_instance;
vespalib::Lock     UrlDataType::_lock;

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
    type->addField(Field(UrlDataType::FIELD_ALL,     *DataType::STRING, true));
    type->addField(Field(UrlDataType::FIELD_SCHEME,  *DataType::STRING, true));
    type->addField(Field(UrlDataType::FIELD_HOST,    *DataType::STRING, true));
    type->addField(Field(UrlDataType::FIELD_PORT,    *DataType::STRING, true));
    type->addField(Field(UrlDataType::FIELD_PATH,    *DataType::STRING, true));
    type->addField(Field(UrlDataType::FIELD_QUERY,   *DataType::STRING, true));
    type->addField(Field(UrlDataType::FIELD_FRAGMENT,*DataType::STRING, true));
    return type;
}

const StructDataType &
UrlDataType::getInstance()
{
    if (_instance.get() == NULL) {
        vespalib::LockGuard guard(_lock);
        if (_instance.get() == NULL) {
            _instance = createInstance();
        }
    }
    return *_instance;
}

} // document
