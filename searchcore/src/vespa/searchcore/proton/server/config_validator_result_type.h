// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton {
namespace configvalidator {

/**
 * The various results of a schema check.
 * All but OK means that the new schema should be rejected.
 */
enum class ResultType
{
    OK,
    DATA_TYPE_CHANGED,
    COLLECTION_TYPE_CHANGED,
    INDEX_ASPECT_ADDED,
    INDEX_ASPECT_REMOVED,
    ATTRIBUTE_ASPECT_ADDED,
    ATTRIBUTE_ASPECT_REMOVED,
    ATTRIBUTE_FAST_ACCESS_ADDED,
    ATTRIBUTE_FAST_ACCESS_REMOVED,
    ATTRIBUTE_TENSOR_TYPE_CHANGED
};

} // namespace proton::configvalidator
} // namespace proton
