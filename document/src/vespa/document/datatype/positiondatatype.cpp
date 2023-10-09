// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "positiondatatype.h"

namespace document {

namespace {

const vespalib::string ZCURVE("_zcurve");

}

const vespalib::string PositionDataType::STRUCT_NAME("position");
const vespalib::string PositionDataType::FIELD_X("x");
const vespalib::string PositionDataType::FIELD_Y("y");

StructDataType::UP
PositionDataType::createInstance()
{
    auto type = std::make_unique<StructDataType>(PositionDataType::STRUCT_NAME);
    type->addField(Field(PositionDataType::FIELD_X, *DataType::INT));
    type->addField(Field(PositionDataType::FIELD_Y, *DataType::INT));
    return type;
}

const StructDataType &
PositionDataType::getInstance()
{
    static StructDataType::UP instance = createInstance();
    return *instance;
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
