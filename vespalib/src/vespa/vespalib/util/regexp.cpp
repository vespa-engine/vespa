// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <sys/types.h>
#include <regex.h>
#include <vespa/vespalib/util/regexp.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/vespalib/util/exceptions.h>
#include <vespa/log/log.h>

LOG_SETUP(".vespalib.util.regexp");

namespace vespalib {

namespace {

bool has_option(vespalib::stringref re) {
    return (re.find('|') != re.npos);
}

bool maybe_none(char c) {
    return ((c == '{') ||
            (c == '*') ||
            (c == '?'));
}

const vespalib::string special("^|()[]{}.*?+\\$");
bool is_special(char c) { return special.find(c) != special.npos; }

vespalib::string escape(vespalib::stringref str) {
    vespalib::string result;
    for (char c: str) {
        if (is_special(c)) {
            result.push_back('\\');
        }
        result.push_back(c);
    }
    return result;
}

} // namespace vespalib::<unnamed>

vespalib::string
RegexpUtil::get_prefix(vespalib::stringref re)
{
    vespalib::string prefix;
    if ((re.size() > 0) && (re.data()[0] == '^') && !has_option(re)) {
        const char *end = re.data() + re.size();
        const char *pos = re.data() + 1;
        for (; (pos < end) && !is_special(*pos); ++pos) {
            prefix.push_back(*pos);
        }
        if ((pos < end) && maybe_none(*pos) && !prefix.empty()) {
            prefix.resize(prefix.size() - 1); // pop_back
        }
    }
    return prefix;
}

vespalib::string
RegexpUtil::make_from_suffix(vespalib::stringref suffix)
{
    return escape(suffix) + "$";
}

vespalib::string
RegexpUtil::make_from_substring(vespalib::stringref substring)
{
    return escape(substring);
}

} // namespace vespalib
