// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryenvironment.h"

namespace search::fef::test {

QueryEnvironment::QueryEnvironment(IndexEnvironment *env)
    : _indexEnv(env),
      _terms(),
      _properties(),
      _locations(),
      _attrCtx((env == nullptr) ? attribute::IAttributeContext::UP() : env->getAttributeMap().createContext())
{
}

QueryEnvironment::~QueryEnvironment() { }

}
