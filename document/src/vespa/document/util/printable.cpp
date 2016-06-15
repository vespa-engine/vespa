// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/document/util/printable.h>

namespace document {

std::string Printable::toString(bool verbose, const std::string& indent) const
{
    std::ostringstream o;
    print(o, verbose, indent);
    return o.str();
}

}
