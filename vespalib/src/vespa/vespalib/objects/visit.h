// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
    class Identifiable;
    class ObjectVisitor;
}

void visit(vespalib::ObjectVisitor &self, std::string_view name, const vespalib::Identifiable *obj);
void visit(vespalib::ObjectVisitor &self, std::string_view name, const vespalib::Identifiable &obj);
void visit(vespalib::ObjectVisitor &self, std::string_view name, bool value);
void visit(vespalib::ObjectVisitor &self, std::string_view name, char value);
void visit(vespalib::ObjectVisitor &self, std::string_view name, signed char value);
void visit(vespalib::ObjectVisitor &self, std::string_view name, unsigned char value);
void visit(vespalib::ObjectVisitor &self, std::string_view name, short value);
void visit(vespalib::ObjectVisitor &self, std::string_view name, unsigned short value);
void visit(vespalib::ObjectVisitor &self, std::string_view name, int value);
void visit(vespalib::ObjectVisitor &self, std::string_view name, unsigned int value);
void visit(vespalib::ObjectVisitor &self, std::string_view name, long value);
void visit(vespalib::ObjectVisitor &self, std::string_view name, unsigned long value);
void visit(vespalib::ObjectVisitor &self, std::string_view name, long long value);
void visit(vespalib::ObjectVisitor &self, std::string_view name, unsigned long long value);
void visit(vespalib::ObjectVisitor &self, std::string_view name, float value);
void visit(vespalib::ObjectVisitor &self, std::string_view name, double value);
void visit(vespalib::ObjectVisitor &self, std::string_view name, std::string_view value);
void visit(vespalib::ObjectVisitor &self, std::string_view name, const char *value);
