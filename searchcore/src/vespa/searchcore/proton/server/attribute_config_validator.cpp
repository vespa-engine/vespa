// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.server.attribute_config_validator");
#include "attribute_config_validator.h"
#include <vespa/eval/eval/value_type.h>
#include <vespa/vespalib/util/stringfmt.h>


using vespa::config::search::AttributesConfig;
using vespalib::make_string;
using vespalib::eval::ValueType;
using proton::configvalidator::ResultType;
using proton::configvalidator::Result;

namespace proton {

namespace {

Result
checkFastAccess(const AttributesConfig &cfg1,
                const AttributesConfig &cfg2,
                ResultType type,
                const vespalib::string &typeStr)
{
    for (const auto &attr1 : cfg1.attribute) {
        if (attr1.fastaccess) {
            for (const auto &attr2 : cfg2.attribute) {
                if (attr1.name == attr2.name && !attr2.fastaccess) {
                    return Result(type,
                            make_string("Trying to %s 'fast-access' to attribute '%s'",
                                    typeStr.c_str(), attr1.name.c_str()));
                }
            }
        }
    }
    return Result();
}

Result
checkFastAccessAdded(const AttributesConfig &newCfg,
                     const AttributesConfig &oldCfg)
{
    return checkFastAccess(newCfg, oldCfg, ResultType::ATTRIBUTE_FAST_ACCESS_ADDED, "add");
}

Result
checkFastAccessRemoved(const AttributesConfig &newCfg,
                       const AttributesConfig &oldCfg)
{
    return checkFastAccess(oldCfg, newCfg, ResultType::ATTRIBUTE_FAST_ACCESS_REMOVED, "remove");
}

Result
checkTensorTypeChanged(const AttributesConfig &newCfg,
                       const AttributesConfig &oldCfg)
{
    for (const auto &newAttr : newCfg.attribute) {
        for (const auto &oldAttr : oldCfg.attribute) {
            if ((newAttr.name == oldAttr.name) &&
                (ValueType::from_spec(newAttr.tensortype) != ValueType::from_spec(oldAttr.tensortype)))
            {
                return Result(ResultType::ATTRIBUTE_TENSOR_TYPE_CHANGED,
                                  make_string("Tensor type has changed from '%s' -> '%s' for attribute '%s'",
                                              oldAttr.tensortype.c_str(), newAttr.tensortype.c_str(), newAttr.name.c_str()));
            }
        }
    }
    return Result();
}

}

Result
AttributeConfigValidator::validate(const AttributesConfig &newCfg,
                                   const AttributesConfig &oldCfg)
{
    Result res;
    if (!(res = checkFastAccessAdded(newCfg, oldCfg)).ok()) return res;
    if (!(res = checkFastAccessRemoved(newCfg, oldCfg)).ok()) return res;
    if (!(res = checkTensorTypeChanged(newCfg, oldCfg)).ok()) return res;
    return Result();
}

} // namespace proton
