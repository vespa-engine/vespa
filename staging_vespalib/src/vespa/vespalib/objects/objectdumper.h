// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "objectvisitor.h"

namespace vespalib {

/**
 * This is a concrete object visitor that will build up a structured
 * human-readable string representation of an object.
 **/
class ObjectDumper : public ObjectVisitor
{
private:
    vespalib::string _str;
    int         _indent;
    int         _currIndent;

    /**
     * Add a number of spaces equal to the current indent to the
     * string we are building.
     **/
    void addIndent();

    /**
     * Add a complete line of output. Appropriate indentation will be
     * added before the given string and a newline will be added after
     * it.
     *
     * @param line the line we want to add
     **/
    void addLine(const vespalib::string &line);

    /**
     * Open a subscope by increasing the current indent level
     **/
    void openScope();

    /**
     * Close a subscope by decreasing the current indent level
     **/
    void closeScope();

public:
    /**
     * Create an object dumper with the given indent size; default is
     * 4 spaces per indent level.
     *
     * @param indent indent size in number of spaces
     **/
    ObjectDumper(int indent = 4);
    ~ObjectDumper();

    /**
     * Obtain the created object string representation. This object
     * should be invoked after the complete object structure has been
     * visited.
     *
     * @return object string representation
     **/
    vespalib::string toString() const { return _str; }

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
