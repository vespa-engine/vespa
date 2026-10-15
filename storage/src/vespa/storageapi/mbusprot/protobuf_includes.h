// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

// because abseil-cpp #defines LOG and VLOG, we need to include this
// before using vespa/log/log.h

#ifdef LOG
#error "must include protobuf files before vespa/log includes"
#endif
#include <vespa/storageapi/mbusprot/feed.pb.h>
#include <vespa/storageapi/mbusprot/inspect.pb.h>
#include <vespa/storageapi/mbusprot/maintenance.pb.h>
#include <vespa/storageapi/mbusprot/visiting.pb.h>

#undef LOG
#undef VLOG
