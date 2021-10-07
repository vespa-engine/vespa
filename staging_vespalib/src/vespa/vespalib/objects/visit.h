// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
    class Identifiable;
    class ObjectVisitor;
}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const vespalib::Identifiable *obj);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const vespalib::Identifiable &obj);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, bool value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, char value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, signed char value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, unsigned char value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, short value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, unsigned short value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, int value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, unsigned int value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, long value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, unsigned long value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, long long value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, unsigned long long value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, float value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, double value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const vespalib::string &value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, vespalib::stringref value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const char *value);
