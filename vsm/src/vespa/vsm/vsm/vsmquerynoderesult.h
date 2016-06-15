// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/query/querynoderesultbase.h>

namespace vsm
{

class VsmQueryNodeResult : public search::QueryNodeResultBase
{
 public:
  DUPLICATE(VsmQueryNodeResult);
  virtual ~VsmQueryNodeResult() { }
  virtual bool evaluate() const { return true; }
  virtual void reset()          { }
};

}

