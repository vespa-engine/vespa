// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

namespace search::docsummary::command {

/**
 * This contains all commands that map to specific docsum field writer(s) when setting up a summary result class.
 */

extern const std::string abs_distance;
extern const std::string attribute;
extern const std::string attribute_combiner;
extern const std::string attribute_tokens;
extern const std::string copy;
extern const std::string documentid;
extern const std::string dynamic_teaser;
extern const std::string empty;
extern const std::string geo_position;
extern const std::string matched_attribute_elements_filter;
extern const std::string matched_elements_filter;
extern const std::string positions;
extern const std::string rank_features;
extern const std::string summary_features;
extern const std::string tokens;

}
