// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include <vbench/core/string.h>
#include <vespa/vespalib/locale/c.h>
#include <cerrno>

namespace vbench {

/**
 * Special benchmark headers that can be returned from the QRS. All
 * values are converted to double. They are also bundled with a flag
 * indicating whether they have been set or not.
 **/
struct BenchmarkHeaders
{
    struct Value {
        double value;
        bool   is_set;
        Value() : value(0.0), is_set(false) {}
        void set(const string &string_value) {
            char *end;
            errno = 0;
            double val = vespalib::locale::c::strtod(string_value.c_str(), &end);
            if (errno == 0 && *end == '\0') {
                value = val;
                is_set = true;
            }
        }
    };
    Value num_hits;
    Value num_fasthits;
    Value num_grouphits;
    Value num_errors;
    Value total_hit_count;
    Value num_docsums;
    Value query_hits;
    Value query_offset;
    Value search_time;
    Value attr_time;
    Value fill_time;
    Value docs_searched;
    Value nodes_searched;
    Value full_coverage;
    void handleHeader(const string &name, const string &string_value);
    string toString() const;
};

} // namespace vbench

