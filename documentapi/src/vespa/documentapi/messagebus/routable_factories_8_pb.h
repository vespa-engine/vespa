// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

// because abseil-cpp #defines LOG and VLOG, we need to include this
// before using vespa/log/log.h

#ifdef LOG
#error "must include protobuf files before vespa/log includes"
#endif
#include <vespa/documentapi/messagebus/docapi_common.pb.h>
#include <vespa/documentapi/messagebus/docapi_feed.pb.h>
#include <vespa/documentapi/messagebus/docapi_inspect.pb.h>
#include <vespa/documentapi/messagebus/docapi_visiting.pb.h>
#undef LOG
#undef VLOG
