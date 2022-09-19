// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search::docsummary::command {

/**
 * This contains all commands that map to specific docsum field writer(s) when setting up a summary result class.
 */

extern const vespalib::string abs_distance;
extern const vespalib::string attribute;
extern const vespalib::string attribute_combiner;
extern const vespalib::string copy;
extern const vespalib::string documentid;
extern const vespalib::string dynamic_teaser;
extern const vespalib::string empty;
extern const vespalib::string geo_position;
extern const vespalib::string matched_attribute_elements_filter;
extern const vespalib::string matched_elements_filter;
extern const vespalib::string positions;
extern const vespalib::string rank_features;
extern const vespalib::string summary_features;

}
