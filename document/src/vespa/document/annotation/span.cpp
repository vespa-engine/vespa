// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".span");

#include "span.h"

using std::ostream;
using std::string;

namespace document {

void Span::print(ostream& out, bool, const string&) const {
    out << "Span(" << _from << ", " << _length << ")";
}

}  // namespace document
