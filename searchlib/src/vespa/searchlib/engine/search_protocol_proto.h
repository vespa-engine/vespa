// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wnonnull"
#include <vespa/searchlib/engine/search_protocol.pb.h>
#pragma GCC diagnostic pop
// -Winline is reported at the call site of the generated add_*() methods (they are
// templates, inlined/instantiated where used), not while parsing this header, so
// suppressing it inside the push/pop above has no effect. Left enabled (not popped)
// for the rest of any translation unit that includes this header.
#pragma GCC diagnostic ignored "-Winline"
