// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".index.indexbuilder");
#include "indexbuilder.h"

namespace search
{

namespace index
{


IndexBuilder::IndexBuilder(const Schema &schema)
    : _schema(schema)
{
}


IndexBuilder::~IndexBuilder()
{
}


} // namespace index

} // namespace search
