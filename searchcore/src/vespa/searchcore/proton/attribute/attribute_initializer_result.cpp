// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_initializer_result.h"


namespace proton {

AttributeInitializerResult::AttributeInitializerResult(const AttributeVectorSP &attr,
                                                       bool hideFromReading,
                                                       bool hideFromWriting)
    : _attr(attr),
      _hideFromReading(hideFromReading),
      _hideFromWriting(hideFromWriting)
{
}

AttributeInitializerResult::~AttributeInitializerResult()
{
}

} // namespace proton
