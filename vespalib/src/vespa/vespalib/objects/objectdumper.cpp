// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "objectdumper.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace vespalib {

using string = vespalib::string;

void
ObjectDumper::addIndent()
{
    int n = _currIndent;
    if (n < 0) {
        n = 0;
    }
    _str.append(string(n, ' '));
}

void
ObjectDumper::addLine(const string &line)
{
    addIndent();
    _str.append(line);
    _str.push_back('\n');
}

void
ObjectDumper::openScope()
{
    _currIndent += _indent;
}

void
ObjectDumper::closeScope()
{
    _currIndent -= _indent;
}

ObjectDumper::ObjectDumper(int indent)
    : _str(),
      _indent(indent),
      _currIndent(0)
{
}

ObjectDumper::~ObjectDumper() = default;

//-----------------------------------------------------------------------------

void
ObjectDumper::openStruct(std::string_view name, std::string_view type)
{
    if (name.empty()) {
        addLine(type + " {");
    } else {
        addLine((string(name).append(": ").append(type).append(" {")));
    }
    openScope();
}

void
ObjectDumper::closeStruct()
{
    closeScope();
    addLine("}");
}

void
ObjectDumper::visitBool(std::string_view name, bool value)
{
    addLine(string(name).append(value ? ": true" : ": false"));
}

void
ObjectDumper::visitInt(std::string_view name, int64_t value)
{
    addLine(make_string("%s: %" PRId64 "", string(name).c_str(), value));
}

void
ObjectDumper::visitFloat(std::string_view name, double value)
{
    addLine(make_string("%s: %g", string(name).c_str(), value));
}

void
ObjectDumper::visitString(std::string_view name, std::string_view value)
{
    addLine(string(name).append(": '").append(value).append('\''));
}

void
ObjectDumper::visitNull(std::string_view name)
{
    addLine(name + ": <NULL>");
}

void
ObjectDumper::visitNotImplemented()
{
    addLine("<member visit not implemented>");
}

} // namespace vespalib
