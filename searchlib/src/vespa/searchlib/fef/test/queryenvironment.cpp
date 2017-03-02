// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryenvironment.h"

namespace search {
namespace fef {
namespace test {

QueryEnvironment::QueryEnvironment(IndexEnvironment *env)
    : _indexEnv(env),
      _terms(),
      _properties(),
      _location(),
      _attrCtx((env == NULL) ? attribute::IAttributeContext::UP() : env->getAttributeManager().createContext())
{
}

QueryEnvironment::~QueryEnvironment() { }

} // namespace test
} // namespace fef
} // namespace search
