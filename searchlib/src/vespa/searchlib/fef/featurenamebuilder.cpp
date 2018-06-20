// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "featurenamebuilder.h"
#include "featurenameparser.h"

namespace {

// ref: http://en.wikipedia.org/wiki/ASCII
// note: we also consider space to be printable
bool isPrintable(char c) {
    return (static_cast<unsigned char>(c) >= 32 &&
            static_cast<unsigned char>(c) <= 126);
}

bool isSpace(char c) {
    switch (c) {
    case ' ':
    case '\t':
    case '\n':
    case '\r':
    case '\f':
        return true;
    default:
        return false;
    }
}

bool isBlank(const vespalib::string &str) {
    for (uint32_t i = 0; i < str.size(); ++i) {
        if (!isSpace(str[i])) {
            return false;
        }
    }
    return true;
}

void appendQuoted(char c, vespalib::string &str) {
    switch (c) {
    case '\\':
        str.append("\\\\");
        break;
    case '"':
        str.append("\\\"");
        break;
    case '\t':
        str.append("\\t");
        break;
    case '\n':
        str.append("\\n");
        break;
    case '\r':
        str.append("\\r");
        break;
    case '\f':
        str.append("\\f");
        break;
    default:
        if (isPrintable(c)) {
            str.push_back(c);
        } else {
            const char *lookup = "0123456789abcdef";
            str.append("\\x");
            str.push_back(lookup[(c >> 4) & 0xf]);
            str.push_back(lookup[c & 0xf]);
        }
    }
}

vespalib::string quoteString(const vespalib::string &str)
{
    vespalib::string res;
    res.push_back('"');
    for (uint32_t i = 0; i < str.size(); ++i) {
        appendQuoted(str[i], res);
    }
    res.push_back('"');
    return res;
}

} // namespace <unnamed>

namespace search {
namespace fef {

FeatureNameBuilder::FeatureNameBuilder()
    : _baseName(),
      _parameters(),
      _output()
{
}

FeatureNameBuilder::~FeatureNameBuilder()
{
}

FeatureNameBuilder &
FeatureNameBuilder::baseName(const vespalib::string &str)
{
    _baseName = str;
    return *this;
}

FeatureNameBuilder &
FeatureNameBuilder::parameter(const vespalib::string &str, bool exact)
{
    if (str.empty() || (!exact && isBlank(str))) {
        _parameters.push_back("");
    } else {
        FeatureNameParser parser(str);
        if (!parser.valid() || (exact && str != parser.featureName())) {
            _parameters.push_back(quoteString(str));
        } else {
            _parameters.push_back(parser.featureName());
        }
    }
    return *this;
}

FeatureNameBuilder &
FeatureNameBuilder::clearParameters()
{
    _parameters.resize(0);
    return *this;
}

FeatureNameBuilder &
FeatureNameBuilder::output(const vespalib::string &str)
{
    _output = str;
    return *this;
}

vespalib::string
FeatureNameBuilder::buildName() const
{
    vespalib::string ret;
    if (!_baseName.empty()) {
        ret = _baseName;
        if (!_parameters.empty()) {
            ret += "(";
            for (uint32_t i = 0; i < _parameters.size(); ++i) {
                if (i > 0) {
                    ret += ",";
                }
                ret += _parameters[i];
            }
            ret += ")";
        }
        if (!_output.empty()) {
            ret += ".";
            ret += _output;
        }
    }
    return ret;
}

} // namespace fef
} // namespace search
