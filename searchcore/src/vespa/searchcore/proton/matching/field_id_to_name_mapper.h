// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/iindexenvironment.h>
#include <vespa/searchlib/fef/fieldinfo.h>

namespace proton::matching {

class FieldIdToNameMapper {
public:
    const std::string & lookup(uint32_t fieldId) const {
        static const std::string lookupFailed;
        if (auto fieldInfo = _indexEnv.getField(fieldId)) {
            return fieldInfo->name();
        }
        return lookupFailed;
    }
    FieldIdToNameMapper(const search::fef::IIndexEnvironment &indexEnv)
      : _indexEnv(indexEnv)
    {}
private:
    const search::fef::IIndexEnvironment &_indexEnv;
};

}
