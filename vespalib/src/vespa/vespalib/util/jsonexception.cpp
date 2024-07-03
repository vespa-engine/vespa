// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "jsonexception.h"

namespace vespalib {

VESPA_IMPLEMENT_EXCEPTION_SPINE(JsonStreamException);

JsonStreamException::JsonStreamException(std::string_view reason, std::string_view history,
                                         std::string_view location, int skipStack)
    : Exception(reason + (history.empty() ? "" : "\nHistory:\n" + history), 
                location, skipStack + 1),
      _reason(reason)
{ }

JsonStreamException::~JsonStreamException() { }

}
