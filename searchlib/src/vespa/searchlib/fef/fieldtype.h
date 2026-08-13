// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::fef {

/**
 * Scoped and typesafe enum used to indicate the type of a field.
 *
 * NONE is used only by the special "no field" instance held first (id 0) in
 * the field table of every IIndexEnvironment; no declared field has this type.
 **/
enum class FieldType { NONE = 0, INDEX = 1, ATTRIBUTE = 2, HIDDEN_ATTRIBUTE = 3, VIRTUAL = 4 };

} // namespace search::fef
