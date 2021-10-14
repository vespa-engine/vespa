// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "visit.hpp"

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const vespalib::Identifiable *obj) {
    if (obj != 0) {
        self.openStruct(name, obj->getClass().name());
        obj->visitMembers(self);
        self.closeStruct();
    } else {
        self.visitNull(name);
    }
}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const vespalib::Identifiable &obj) {
    visit(self, name, &obj);
}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, bool value) {
    self.visitBool(name, value);
}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, char value)
{
    self.visitInt(name, value);
}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, signed char value)
{
    self.visitInt(name, value);
}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, unsigned char value)
{
    self.visitInt(name, value);
}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, short value)
{
    self.visitInt(name, value);
}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, unsigned short value)
{
    self.visitInt(name, value);
}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, int value) {
    self.visitInt(name, value);
}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, unsigned int value) {
    self.visitInt(name, value);
}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, long value) {
    self.visitInt(name, value);
}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, unsigned long value) {
    self.visitInt(name, value);
}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, long long value) {
    self.visitInt(name, value);
}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, unsigned long long value) {
    self.visitInt(name, value);
}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, float value) {
    self.visitFloat(name, value);
}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, double value) {
    self.visitFloat(name, value);
}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const vespalib::string &value) {
    self.visitString(name, value);
}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const std::string &value) {
    self.visitString(name, value);
}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const char *value) {
    if (value != 0) {
        visit(self, name, vespalib::string(value));
    } else {
        self.visitNull(name);
    }
}
