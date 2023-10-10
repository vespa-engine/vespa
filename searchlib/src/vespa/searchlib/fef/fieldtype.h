// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::fef {

/**
 * Scoped and typesafe enum used to indicate the type of a field.
 **/
enum class FieldType {
    INDEX = 1,
    ATTRIBUTE = 2,
    HIDDEN_ATTRIBUTE = 3,
    VIRTUAL = 4
};

}
