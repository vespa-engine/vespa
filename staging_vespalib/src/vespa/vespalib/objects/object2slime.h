// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "objectvisitor.h"
#include <vector>

namespace vespalib {

namespace slime { class Cursor; }

/**
 * This is a concrete object visitor that will build up a structured
 * slime representation of an object.
 **/
class Object2Slime : public ObjectVisitor
{
private:
    slime::Cursor * _cursor;
    std::vector<slime::Cursor *> _stack;

public:
    Object2Slime(slime::Cursor & cursor);
    ~Object2Slime();

    void openStruct(const vespalib::string &name, const vespalib::string &type) override;
    void closeStruct() override;
    void visitBool(const vespalib::string &name, bool value) override;
    void visitInt(const vespalib::string &name, int64_t value) override;
    void visitFloat(const vespalib::string &name, double value) override;
    void visitString(const vespalib::string &name, const vespalib::string &value) override;
    void visitNull(const vespalib::string &name) override;
    void visitNotImplemented() override;
};

} // namespace vespalib
