// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configparser.h"
#include "exceptions.h"
#include "misc.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/locale/c.h>

namespace config {

void ConfigParser::throwNoDefaultValue(vespalib::stringref key) {
    throw InvalidConfigException("Config parameter " + key + " has no "
            "default value and is not specified in config", VESPA_STRLOC);
}

vespalib::string
ConfigParser::deQuote(const vespalib::string & source)
{
    const char *src = source.c_str();
    const char *s = src;
    std::vector<char> dst(1+source.length());
    char *d = &dst[0];
    bool isQuoted;

    if (*s == '"') {
        isQuoted = true;
        ++s;
    } else {
        isQuoted = false;
    }

    while (1) {
        const char hexchars[] = "0123456789abcdefABCDEF";

        char c = *s++;
        if (isQuoted && c == '\\') { // Escape char only allowed in quotes
            char escaped = *s++;
            switch (escaped) {
            case 'n':
                *d++ = '\n';
                break;
            case 'r':
                *d++ = '\r';
                break;
            case '\\':
                *d++ = '\\';
                break;
            case '"':
                *d++ = '"';
                break;
            case 'x':
                // XXX we should have a utility routine for this
                if (strchr(hexchars, s[0]) && strchr(hexchars, s[1])) {
                    unsigned int hex = 0;
                    sscanf(s, "%2x", &hex);
                    s += 2;
                    *d++ = hex;
                } else {
                    throwInvalid("Invalid \\x escape \\x%.2s in %s", s, src);
                }
                break;
            default:
                throwInvalid("Invalid escape character in %s: \\%c", src, escaped);
                break;
            }
        } else if (!c) {
            if (isQuoted) {
                throwInvalid("Unterminated quotes in (len=%u) '%s'", (uint32_t)strlen(src), src);
            }
            break;
        } else if (c == '"') {
            if (!isQuoted) {
                throwInvalid("Quote character inside unquoted string in '%s'", src);
            }
            if (*s) throwInvalid("string must terminate after quotes: '%s'", src);
            break;
        } else {
            *d++ = c;
        }
    }
    *d = 0;
    return vespalib::string(&dst[0], d - &dst[0]);
}

namespace {

bool
getValueForKey(vespalib::stringref key, vespalib::stringref line,
               vespalib::string& retval)
{
    if (line.length() <= key.length()) {
        return false;
    }

    vespalib::stringref sub = line.substr(0, key.length());
    if (sub != key) {
        return false;
    }

    int pos = key.length();

    if (line[pos] == ' ' || line[pos] == '.') {
        retval = line.substr(pos + 1);
        return true;
    }
    if (line[pos] == '[') {
        retval = line.substr(pos);
        // We don't need array declarations
        if (retval[retval.size() - 1] == ']')
            return false;
        return true;
    }
    if (line[pos] == '{') {
        retval = line.substr(pos);
        // Skip empty maps
        if (retval[retval.size() - 1] == '}')
            return false;
        return true;
    }

    return false;
}

}

StringVector
ConfigParser::getLinesForKey(vespalib::stringref key, Cfg lines)
{
    StringVector retval;

    for (uint32_t i = 0; i < lines.size(); i++) {
        vespalib::string value;

        if (getValueForKey(key, lines[i], value)) {
            retval.push_back(value);
        }
    }

    return retval;
}

std::set<vespalib::string>
ConfigParser::getUniqueNonWhiteSpaceLines(Cfg config) {
    std::set<vespalib::string> unique;
    for (uint32_t i = 0; i < config.size(); i++) {
        vespalib::string line = stripWhitespace(config[i]);
        if (!line.empty()) {
            unique.insert(line);
        }
    }
    return unique;
}

void
ConfigParser::stripLinesForKey(vespalib::stringref key,
                               std::set<vespalib::string>& config)
{
    vespalib::string value;
    for (std::set<vespalib::string>::iterator it = config.begin(); it != config.end();) {
        if (getValueForKey(key, *it, value)) {
            std::set<vespalib::string>::iterator it2 = it++;
            config.erase(it2);
        } else {
            ++it;
        }
    }
}

std::map<vespalib::string, StringVector>
ConfigParser::splitMap(Cfg config)
{
    std::map<vespalib::string, StringVector> items;

    vespalib::string lastValue;

    // First line contains item count, skip that.
    for (uint32_t i = 0; i < config.size(); i++) {
        size_t pos = config[i].find("}");

        if (config[i].size() < 3 || config[i][0] != '{'
            || pos == vespalib::string::npos)
        {
            throw InvalidConfigException(
                    "Value '" + config[i] + "' is not a valid map "
                    "specification.", VESPA_STRLOC);
        }

        vespalib::string key = deQuote(config[i].substr(1, pos - 1));
        vespalib::string value = config[i].substr(pos + 1);

        if (key != lastValue) {
            items[key] = StringVector();
            lastValue = key;
        }

        if (value[0] == '.') {
            items[key].push_back(value.substr(1));
        } else {
            items[key].push_back(value);
        }
    }
    return items;
}

std::vector<StringVector>
ConfigParser::splitArray(Cfg config)
{
    std::vector<StringVector> items;

    vespalib::string lastValue;

    // First line contains item count, skip that.
    for (uint32_t i = 0; i < config.size(); i++) {
        size_t pos = config[i].find("]");

        if (config[i].size() < 3 || config[i][0] != '['
            || pos == vespalib::string::npos)
        {
            throw InvalidConfigException(
                    "Value '" + config[i] + "' is not a valid array "
                    "specification.", VESPA_STRLOC);
        }

        vespalib::string key = config[i].substr(1, pos - 1);
        vespalib::string value = config[i].substr(pos + 1);

        if (key != lastValue) {
            items.push_back(StringVector());
            lastValue = key;
        }

        if (value[0] == '.') {
            items.back().push_back(value.substr(1));
        } else {
            items.back().push_back(value);
        }
    }
    return items;
}

vespalib::string
ConfigParser::stripWhitespace(vespalib::stringref source)
{
    // Remove leading spaces and return.
    if (source.empty()) {
        return source;
    }
    size_t start = 0;
    bool found = false;
    while (!found && start < source.size()) {
        switch (source[start]) {
            case ' ':
            case '\t':
            case '\r':
            case '\f':
                ++start;
                break;
            default:
                found = true;
        }
    }
    size_t stop = source.size() - 1;
    found = false;
    while (!found && stop > start) {
        switch (source[stop]) {
            case ' ':
            case '\t':
            case '\r':
            case '\f':
                --stop;
                break;
            default:
                found = true;
        }
    }
    return source.substr(start, stop - start + 1);
}

vespalib::string
ConfigParser::arrayToString(Cfg array)
{
    vespalib::asciistream ost;
    if (array.size() == 0) {
        ost << "No entries";
    } else {
        for (uint32_t i=0; i<array.size(); ++i) {
            ost << array[i] << "\n";
        }
    }
    return ost.str();
}

template<>
bool
ConfigParser::convert<bool>(const StringVector & config)
{
    if (config.size() != 1) {
        throw InvalidConfigException("Expected single line with bool value, "
                "got " + arrayToString(config), VESPA_STRLOC);
    }
    vespalib::string value = stripWhitespace(deQuote(config[0]));

    if (value == "true") {
        return true;
    } else if (value == "false") {
        return false;
    } else {
        throw InvalidConfigException("Expected bool value, got " + value
                + "instead", VESPA_STRLOC);
    }
}

template<>
int32_t
ConfigParser::convert<int32_t>(const StringVector & config)
{
    if (config.size() != 1) {
        throw InvalidConfigException("Expected single line with int32_t value, "
                "got " + arrayToString(config), VESPA_STRLOC);
    }
    vespalib::string value(deQuote(stripWhitespace(config[0])));

    const char *startp = value.c_str();
    char *endp;
    errno = 0;
    int32_t ret = strtol(startp, &endp, 0);
    int err = errno;
    if (err == ERANGE || err == EINVAL || (*endp != '\0'))
        throw InvalidConfigException("Value " + value + " is not a legal int32_t.", VESPA_STRLOC);
    return ret;
}

template<>
int64_t
ConfigParser::convert<int64_t>(const StringVector & config)
{
    if (config.size() != 1) {
        throw InvalidConfigException("Expected single line with int64_t value, "
                "got " + arrayToString(config), VESPA_STRLOC);
    }
    vespalib::string value(deQuote(stripWhitespace(config[0])));

    const char *startp = value.c_str();
    char *endp;
    errno = 0;
    int64_t ret = strtoll(startp, &endp, 0);
    int err = errno;
    if (err == ERANGE || err == EINVAL || (*endp != '\0'))
        throw InvalidConfigException("Value " + value + " is not a legal int64_t.", VESPA_STRLOC);
    return ret;
}

template<>
double
ConfigParser::convert<double>(const StringVector & config)
{
    if (config.size() != 1) {
        throw InvalidConfigException("Expected single line with double value, "
                "got " + arrayToString(config), VESPA_STRLOC);
    }
    vespalib::string value(deQuote(stripWhitespace(config[0])));

    const char *startp = value.c_str();
    char *endp;
    errno = 0;
    double ret = vespalib::locale::c::strtod(startp, &endp);
    int err = errno;
    if (err == ERANGE || (*endp != '\0')) {
        throw InvalidConfigException("Value " + value + " is not a legal double", VESPA_STRLOC);
    }
    return ret;
}

template<>
vespalib::string
ConfigParser::convert<vespalib::string>(const StringVector & config)
{
    if (config.size() != 1) {
        throw InvalidConfigException("Expected single line with string value, "
                "got " + arrayToString(config), VESPA_STRLOC);
    }

    vespalib::string value = stripWhitespace(config[0]);

    return deQuote(value);
}

} // config
