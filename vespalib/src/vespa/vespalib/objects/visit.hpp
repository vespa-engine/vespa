// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "visit.h"
#include "objectvisitor.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/array.h>
#include "identifiable.hpp"

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
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const vespalib::IdentifiablePtr<T> &ptr) {
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
        ::visit(self, vespalib::make_string("[%u]", i), list[i]);
    }
    self.closeStruct();
}

template<typename T>
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name, const vespalib::Array<T> &list) {
    self.openStruct(name, "vespalib::Array");
    for (uint32_t i = 0; i < list.size(); ++i) {
        ::visit(self, vespalib::make_string("[%u]", i), list[i]);
    }
    self.closeStruct();
}

