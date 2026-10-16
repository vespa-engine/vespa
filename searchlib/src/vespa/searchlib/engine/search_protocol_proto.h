// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

// because abseil-cpp #defines LOG and VLOG, we need to include this
// before using vespa/log/log.h

#ifdef LOG
#error "must include protobuf files before vespa/log includes"
#endif
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wnonnull"
#include <vespa/searchlib/engine/search_protocol.pb.h>
#undef LOG
#undef VLOG
#pragma GCC diagnostic pop
