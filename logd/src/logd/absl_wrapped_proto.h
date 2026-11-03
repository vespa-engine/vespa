// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

// because abseil-cpp #defines LOG and VLOG, we need to include this
// before almost anything else.

#ifdef LOG
#error "must include protobuf files before vespa/log includes"
#endif
#include <logd/log_protocol.pb.h>
#undef LOG
#undef VLOG
