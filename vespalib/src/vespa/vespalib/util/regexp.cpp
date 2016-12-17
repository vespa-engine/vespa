// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <sys/types.h>
#include <regex.h>
#include <vespa/vespalib/util/regexp.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/vespalib/util/exceptions.h>
#include <vespa/log/log.h>

LOG_SETUP(".vespalib.util.regexp");

namespace vespalib {

Regexp::Flags::Flags() :
    _flags(RE_SYNTAX_POSIX_EXTENDED)
{ }

Regexp::Flags &
Regexp::Flags::enableICASE()
{
    _flags |= RE_ICASE;
    return *this;
}

bool
Regexp::compile(const vespalib::stringref & re, Flags flags)
{
    re_set_syntax(flags.flags());
    regex_t *preg = (regex_t *)_data;
    preg->translate = NULL;
    preg->fastmap = static_cast<char *>(malloc(256));
    preg->buffer = NULL;
    preg->allocated = 0;
    const char * error = re_compile_pattern(re.c_str(), re.size(), preg);
    if (error != 0) {
        LOG(warning, "invalid regexp '%s': %s", re.c_str(), error);
        return false;
    }
    if (re_compile_fastmap(preg) != 0) {
        LOG(warning, "re_compile_fastmap failed for regexp '%s'", re.c_str());
        return false;
    }
    return true;
}


Regexp::Regexp(const vespalib::stringref & re, Flags flags)
    : _valid(false),
      _data(new regex_t)
{
    _valid = compile(re, flags);
}

bool
Regexp::match(const vespalib::stringref & s) const
{
    if ( ! valid() ) { return false; }
    regex_t *preg = const_cast<regex_t *>(static_cast<const regex_t *>(_data));
    int pos(re_search(preg, s.c_str(), s.size(), 0, s.size(), NULL));
    if (pos < -1) {
        throw IllegalArgumentException(make_string("re_search failed with code(%d)", pos));
    }
    return pos >= 0;
}

vespalib::string Regexp::replace(const vespalib::stringref & s, const vespalib::stringref & replacement) const
{
    if ( ! valid() ) { return s; }
    regex_t *preg = const_cast<regex_t *>(static_cast<const regex_t *>(_data));
    vespalib::string modified;
    int prev(0);
    for(int pos(re_search(preg, s.c_str(), s.size(), 0, s.size(), NULL));
        pos >=0;
        pos = re_search(preg, s.c_str()+prev, s.size()-prev, 0, s.size()-prev, NULL))
    {
        modified += s.substr(prev, pos);
        modified += replacement;
        int count = re_match(preg, s.c_str()+prev, s.size()-prev, pos, NULL);
        prev += pos + count;
    }
    modified += s.substr(prev);
    return modified;
}

Regexp::~Regexp()
{
    regex_t *preg = static_cast<regex_t *>(_data);
    regfree(preg);
    delete preg;
}

namespace {

bool has_option(const vespalib::stringref & re) {
    return (re.find('|') != re.npos);
}

bool maybe_none(char c) {
    return ((c == '{') ||
            (c == '*') ||
            (c == '?'));
}

const vespalib::string special("^|()[]{}.*?+\\$");
bool is_special(char c) { return special.find(c) != special.npos; }

vespalib::string escape(const vespalib::stringref &str) {
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
Regexp::get_prefix(const vespalib::stringref & re)
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
Regexp::make_from_prefix(const vespalib::stringref &prefix)
{
    return "^" + escape(prefix);
}

vespalib::string
Regexp::make_from_suffix(const vespalib::stringref &suffix)
{
    return escape(suffix) + "$";
}

vespalib::string
Regexp::make_from_substring(const vespalib::stringref &substring)
{
    return escape(substring);
}

} // namespace vespalib
