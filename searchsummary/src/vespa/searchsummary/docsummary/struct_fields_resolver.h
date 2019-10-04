// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace search { class IAttributeManager; }

namespace search::docsummary {

/**
 * Class used to resolve which struct sub fields a complex field consists of,
 * based on which attribute vectors are present.
 */
class StructFieldsResolver {
private:
    std::vector<vespalib::string> _mapFields;
    std::vector<vespalib::string> _arrayFields;
    bool _hasMapKey;
    bool _error;

public:
    StructFieldsResolver(const vespalib::string& fieldName, const IAttributeManager& attrMgr);
    ~StructFieldsResolver();
    const std::vector<vespalib::string>& getMapFields() const { return _mapFields; }
    const std::vector<vespalib::string>& getArrayFields() const { return _arrayFields; }
    bool getError() const { return _error; }
};

}

