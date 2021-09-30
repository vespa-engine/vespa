// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "objectvisitor.h"
#include <vector>
#include <memory>

namespace vespalib {

namespace slime { struct Cursor; }

/**
 * This is a concrete object visitor that will build up a structured
 * slime representation of an object.
 **/
class Object2Slime : public ObjectVisitor
{
private:
    std::reference_wrapper<slime::Cursor> _cursor;
    std::vector<std::reference_wrapper<slime::Cursor>> _stack;

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
