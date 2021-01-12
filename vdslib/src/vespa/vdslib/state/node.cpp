// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "node.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <ostream>

namespace storage::lib {

std::ostream &
operator << (std::ostream & os, const Node & node)
{
    return os << node.getType() << '.' << node.getIndex();
}

vespalib::asciistream &
operator << (vespalib::asciistream & os, const Node & node)
{
    return os << node.getType() << '.' << node.getIndex();
}

}
