// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "base.h"

namespace search
{

/**
  This is the base of any item that can be attached to the leafs in a querytree.
  The intention is to put stuff here that are search specific. Fx to differentiate
  between streamed and indexed variants.
*/
class QueryNodeResultBase : public Object
{
 public:
  virtual bool evaluate() const = 0;
  virtual void reset() = 0;
  virtual bool getRewriteFloatTerms() const { return false; }
};

class EmptyQueryNodeResult : public QueryNodeResultBase
{
 public:
  DUPLICATE(EmptyQueryNodeResult);
  virtual ~EmptyQueryNodeResult() { }
  virtual bool evaluate()        const { return true; }
  virtual void reset()                 { }
 private:
};


typedef ObjectContainer<QueryNodeResultBase> QueryNodeResultBaseContainer;
}

