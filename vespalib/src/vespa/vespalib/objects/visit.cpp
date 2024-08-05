// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "visit.hpp"

void visit(vespalib::ObjectVisitor &self, std::string_view name, const vespalib::Identifiable *obj) {
    if (obj != nullptr) {
        self.openStruct(name, obj->getClass().name());
        obj->visitMembers(self);
        self.closeStruct();
    } else {
        self.visitNull(name);
    }
}

void visit(vespalib::ObjectVisitor &self, std::string_view name, const vespalib::Identifiable &obj) {
    visit(self, name, &obj);
}

void visit(vespalib::ObjectVisitor &self, std::string_view name, bool value) {
    self.visitBool(name, value);
}

void visit(vespalib::ObjectVisitor &self, std::string_view name, char value)
{
    self.visitInt(name, value);
}

void visit(vespalib::ObjectVisitor &self, std::string_view name, signed char value)
{
    self.visitInt(name, value);
}

void visit(vespalib::ObjectVisitor &self, std::string_view name, unsigned char value)
{
    self.visitInt(name, value);
}

void visit(vespalib::ObjectVisitor &self, std::string_view name, short value)
{
    self.visitInt(name, value);
}

void visit(vespalib::ObjectVisitor &self, std::string_view name, unsigned short value)
{
    self.visitInt(name, value);
}

void visit(vespalib::ObjectVisitor &self, std::string_view name, int value) {
    self.visitInt(name, value);
}

void visit(vespalib::ObjectVisitor &self, std::string_view name, unsigned int value) {
    self.visitInt(name, value);
}

void visit(vespalib::ObjectVisitor &self, std::string_view name, long value) {
    self.visitInt(name, value);
}

void visit(vespalib::ObjectVisitor &self, std::string_view name, unsigned long value) {
    self.visitInt(name, value);
}

void visit(vespalib::ObjectVisitor &self, std::string_view name, long long value) {
    self.visitInt(name, value);
}

void visit(vespalib::ObjectVisitor &self, std::string_view name, unsigned long long value) {
    self.visitInt(name, value);
}

void visit(vespalib::ObjectVisitor &self, std::string_view name, float value) {
    self.visitFloat(name, value);
}

void visit(vespalib::ObjectVisitor &self, std::string_view name, double value) {
    self.visitFloat(name, value);
}

void visit(vespalib::ObjectVisitor &self, std::string_view name, std::string_view value) {
    self.visitString(name, value);
}

void visit(vespalib::ObjectVisitor &self, std::string_view name, const char *value) {
    if (value != nullptr) {
        visit(self, name, std::string_view(value));
    } else {
        self.visitNull(name);
    }
}
