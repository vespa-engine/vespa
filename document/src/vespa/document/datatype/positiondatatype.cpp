// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "positiondatatype.h"

namespace document {

namespace {

const std::string ZCURVE("_zcurve");

}

const std::string PositionDataType::STRUCT_NAME("position");
const std::string PositionDataType::FIELD_X("x");
const std::string PositionDataType::FIELD_Y("y");

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

std::string
PositionDataType::getZCurveFieldName(const std::string & fieldName)
{
    return fieldName + ZCURVE;
}

std::string_view
PositionDataType::cutZCurveFieldName(std::string_view name)
{
    return name.substr(0, name.size() - 7);
}

bool
PositionDataType::isZCurveFieldName(std::string_view name)
{
    if (name.size() > ZCURVE.size()) {
        return ZCURVE == name.substr(name.size() - ZCURVE.size());
    }
    return false;
}

} // document
