// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::docsummary {

/**
 * Which shape the attribute combiner should write for a complex field, as declared in the summary
 * config. INFER means the shape is deduced from the names of the struct field attributes, which is
 * what config from a config model that does not know about this setting asks for.
 */
enum class CombinerShape { INFER, ARRAY_OF_STRUCT, MAP_OF_STRUCT, MAP_OF_SCALAR };

} // namespace search::docsummary
