// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/objects/cloneable.h>
#include <memory>

namespace search::streaming {

/**
  This is the base of any item that can be attached to the leafs in a querytree.
  The intention is to put stuff here that are search specific. Fx to differentiate
  between streamed and indexed variants.
*/
class QueryNodeResultBase : public vespalib::Cloneable
{
public:
};

class QueryNodeResultFactory {
public:
    virtual ~QueryNodeResultFactory() { }
    virtual bool getRewriteFloatTerms() const { return false; }
    virtual std::unique_ptr<QueryNodeResultBase> create() const { return std::unique_ptr<QueryNodeResultBase>(); }
};
}

