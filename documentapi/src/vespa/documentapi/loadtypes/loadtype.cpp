// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/documentapi/loadtypes/loadtype.h>

namespace documentapi {

const LoadType LoadType::DEFAULT(0, "default", Priority::PRI_NORMAL_3);

void
LoadType::print(vespalib::asciistream & os) const
{
    os << "LoadType(" << getId() << ": " << getName() << ")";
}

} // documentapi
