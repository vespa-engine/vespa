// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vector>
#include <vespa/vespalib/util/stringfmt.h>
#include "objectvisitor.h"
#include "identifiable.h"

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
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const std::string &value);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const char *value);

template<typename T>
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const vespalib::CloneablePtr<T> &ptr) {
    if (ptr.get()) {
        visit(self, name, *ptr);
    } else {
        self.visitNull(name);
    }
}

template<typename T>
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const std::shared_ptr<T> &ptr) {
    if (ptr.get()) {
        visit(self, name, *ptr);
    } else {
        self.visitNull(name);
    }
}

template<typename T>
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const std::unique_ptr<T> &ptr) {
    if (ptr.get()) {
        visit(self, name, *ptr);
    } else {
        self.visitNull(name);
    }
}

template<typename T>
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const vespalib::LinkedPtr<T> &ptr) {
    if (ptr.get()) {
        visit(self, name, *ptr);
    } else {
        self.visitNull(name);
    }
}

template<typename T>
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const vespalib::IdentifiablePtr<T> &ptr) {
    visit(self, name, ptr.get());
}

template<typename T>
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const vespalib::IdentifiableLinkedPtr<T> &ptr) {
    visit(self, name, ptr.get());
}

template<typename T>
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const vespalib::IdentifiableSharedPtr<T> &ptr) {
    visit(self, name, ptr.get());
}

template<typename T>
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const std::vector<T> &list) {
    self.openStruct(name, "std::vector");
    for (uint32_t i = 0; i < list.size(); ++i) {
        visit(self, vespalib::make_string("[%u]", i), list[i]);
    }
    self.closeStruct();
}

template<typename T>
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const vespalib::Array<T> &list) {
    self.openStruct(name, "vespalib::Array");
    for (uint32_t i = 0; i < list.size(); ++i) {
        visit(self, vespalib::make_string("[%u]", i), list[i]);
    }
    self.closeStruct();
}

