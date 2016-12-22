// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "positiondatatype.h"

namespace document {

namespace {

const vespalib::string ZCURVE("_zcurve");

}

StructDataType::UP PositionDataType::_instance;
vespalib::Lock     PositionDataType::_lock;

const vespalib::string PositionDataType::STRUCT_NAME("position");
const vespalib::string PositionDataType::FIELD_X("x");
const vespalib::string PositionDataType::FIELD_Y("y");

StructDataType::UP
PositionDataType::createInstance()
{
    StructDataType::UP type(new StructDataType(PositionDataType::STRUCT_NAME));
    type->addField(Field(PositionDataType::FIELD_X, *DataType::INT, true));
    type->addField(Field(PositionDataType::FIELD_Y, *DataType::INT, true));
    return type;
}

const StructDataType &
PositionDataType::getInstance()
{
    if (_instance.get() == NULL) {
        vespalib::LockGuard guard(_lock);
        if (_instance.get() == NULL) {
            _instance = createInstance();
        }
    }
    return *_instance;
}

vespalib::string
PositionDataType::getZCurveFieldName(const vespalib::string & fieldName)
{
    return fieldName + ZCURVE;
}

vespalib::stringref
PositionDataType::cutZCurveFieldName(vespalib::stringref name)
{
    return name.substr(0, name.size() - 7);
}

bool
PositionDataType::isZCurveFieldName(vespalib::stringref name)
{
    if (name.size() > ZCURVE.size()) {
        return ZCURVE == name.substr(name.size() - ZCURVE.size());
    }
    return false;
}

} // document
