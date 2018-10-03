// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
    class Identifiable;
    class ObjectVisitor;
}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const vespalib::Identifiable *obj);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const vespalib::Identifiable &obj);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, bool value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, int8_t value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, uint8_t value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, int16_t value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, uint16_t value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, int32_t value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, uint32_t value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, int64_t value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, uint64_t value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, float value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, double value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const vespalib::string &value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, vespalib::stringref value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const char *value);
