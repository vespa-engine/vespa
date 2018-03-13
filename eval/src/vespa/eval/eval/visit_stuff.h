// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace vespalib {
class ObjectVisitor;
namespace eval {
enum class Aggr;
class TensorFunction;
namespace visit {
using map_fun_t = double (*)(double);
using join_fun_t = double (*)(double, double);
struct DimList {
    const std::vector<vespalib::string> &list;
    DimList(const std::vector<vespalib::string> &list_in)
        : list(list_in) {}
};
struct FromTo {
    const std::vector<vespalib::string> &from;
    const std::vector<vespalib::string> &to;
    FromTo(const std::vector<vespalib::string> &from_in,
           const std::vector<vespalib::string> &to_in)
        : from(from_in), to(to_in) {}
};
} // namespace vespalib::eval::visit
} // namespace vespalib::eval
} // namespace vespalib

void visit(vespalib::ObjectVisitor &visitor, const vespalib::string &name, const vespalib::eval::TensorFunction &value);
void visit(vespalib::ObjectVisitor &visitor, const vespalib::string &name, vespalib::eval::visit::map_fun_t value);
void visit(vespalib::ObjectVisitor &visitor, const vespalib::string &name, vespalib::eval::visit::join_fun_t value);
void visit(vespalib::ObjectVisitor &visitor, const vespalib::string &name, const vespalib::eval::Aggr &value);
void visit(vespalib::ObjectVisitor &visitor, const vespalib::string &name, const vespalib::eval::visit::DimList &value);
void visit(vespalib::ObjectVisitor &visitor, const vespalib::string &name, const vespalib::eval::visit::FromTo &value);
