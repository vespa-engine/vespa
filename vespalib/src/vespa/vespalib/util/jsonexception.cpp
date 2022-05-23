// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "jsonexception.h"

namespace vespalib {

VESPA_IMPLEMENT_EXCEPTION_SPINE(JsonStreamException);

JsonStreamException::JsonStreamException(stringref reason, stringref history,
                                         stringref location, int skipStack)
    : Exception(reason + (history.empty() ? "" : "\nHistory:\n" + history), 
                location, skipStack + 1),
      _reason(reason)
{ }

JsonStreamException::~JsonStreamException() { }

}
