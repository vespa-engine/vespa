// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "objectdumper.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace vespalib {

void
ObjectDumper::addIndent()
{
    int n = _currIndent;
    if (n < 0) {
        n = 0;
    }
    _str.append(vespalib::string(n, ' '));
}

void
ObjectDumper::addLine(const vespalib::string &line)
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
ObjectDumper::openStruct(const vespalib::string &name, const vespalib::string &type)
{
    if (name.empty()) {
        addLine(make_string("%s {", type.c_str()));
    } else {
        addLine(make_string("%s: %s {", name.c_str(), type.c_str()));
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
ObjectDumper::visitBool(const vespalib::string &name, bool value)
{
    addLine(make_string("%s: %s", name.c_str(), value? "true" : "false"));
}

void
ObjectDumper::visitInt(const vespalib::string &name, int64_t value)
{
    addLine(make_string("%s: %" PRId64 "", name.c_str(), value));
}

void
ObjectDumper::visitFloat(const vespalib::string &name, double value)
{
    addLine(make_string("%s: %g", name.c_str(), value));
}

void
ObjectDumper::visitString(const vespalib::string &name, const vespalib::string &value)
{
    addLine(make_string("%s: '%s'", name.c_str(), value.c_str()));
}

void
ObjectDumper::visitNull(const vespalib::string &name)
{
    addLine(make_string("%s: <NULL>", name.c_str()));
}

void
ObjectDumper::visitNotImplemented()
{
    addLine("<member visit not implemented>");
}

} // namespace vespalib
