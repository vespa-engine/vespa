// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::bmcluster {

enum class BmFeedOperation
{
    PUT_OPERATION,
    UPDATE_OPERATION,
    GET_OPERATION,
    REMOVE_OPERATION
};

}
