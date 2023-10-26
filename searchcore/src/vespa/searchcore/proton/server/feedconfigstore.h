// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/feedoperation/newconfigoperation.h>

namespace proton {

struct FeedConfigStore : NewConfigOperation::IStreamHandler {
    virtual ~FeedConfigStore() {}

};

}  // namespace proton

