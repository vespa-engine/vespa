// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "structureddatatype.h"
#include <vespa/document/base/fieldpath.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/document/base/exceptions.h>

using vespalib::make_string;

namespace document {

IMPLEMENT_IDENTIFIABLE_ABSTRACT(StructuredDataType, DataType);

StructuredDataType::StructuredDataType() = default;

StructuredDataType::StructuredDataType(const vespalib::stringref &name)
    : DataType(name, createId(name))
{
}

StructuredDataType::StructuredDataType(const vespalib::stringref &name, int dataTypeId)
    : DataType(name, dataTypeId)
{
}

bool StructuredDataType::operator==(const DataType& type) const
{
    if (!DataType::operator==(type)) return false;
    return (dynamic_cast<const StructuredDataType*>(&type) != 0);
}

namespace {
uint32_t crappyJavaStringHash(const vespalib::stringref &value) {
    uint32_t h = 0;
    for (uint32_t i = 0; i < value.size(); ++i) {
        h = 31 * h + value[i];
    }
    return h;
}
}  // namespace

int32_t StructuredDataType::createId(const vespalib::stringref &name)
{
    if (name == "document") {
        return 8;
    }
    // This should be equal to java implementation if name only has 7-bit
    // ASCII characters. Probably screwed up otherwise, but generated ids
    // should only be used in testing anyways. In production this will be
    // set from the document manager config.
    vespalib::asciistream ost;
    ost << name << ".0";  // Hardcode version 0 (version is not supported).
    return crappyJavaStringHash(ost.str());
}

void
StructuredDataType::onBuildFieldPath(FieldPath & path, const vespalib::stringref & remainFieldName) const
{
    vespalib::stringref currFieldName(remainFieldName);
    vespalib::stringref subFieldName;

    for (uint32_t i = 0; i < remainFieldName.size(); i++) {
        if (remainFieldName[i] == '.') {
            currFieldName = remainFieldName.substr(0, i);
            subFieldName = remainFieldName.substr(i + 1);
            break;
        } else if (remainFieldName[i] == '{' || remainFieldName[i] == '[') {
            currFieldName = remainFieldName.substr(0, i);
            subFieldName = remainFieldName.substr(i);
            break;
        }
    }

    // LOG(debug, "Field %s of datatype %s split into %s and %s",
    //     remainFieldName.c_str(), getName().c_str(), currFieldName.c_str(), subFieldName.c_str());
    if (hasField(currFieldName)) {
        const document::Field &fp = getField(currFieldName);
        fp.getDataType().buildFieldPath(path, subFieldName);

        path.insert(path.begin(), std::make_unique<FieldPathEntry>(fp));
    } else {
        throw FieldNotFoundException(currFieldName, make_string("Invalid field path '%s', no field named '%s'",
                                                   remainFieldName.c_str(), currFieldName.c_str()));
    }
}

} // document
