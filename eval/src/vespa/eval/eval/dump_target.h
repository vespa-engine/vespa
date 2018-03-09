// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vector>
#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
namespace eval {

struct TensorFunction;
struct DumpTargetBackend;
enum class Aggr;

class DumpTarget
{
private:
    DumpTargetBackend &_back_end;
    DumpTarget(DumpTargetBackend &back_end);
    DumpTarget(DumpTargetBackend &back_end, size_t level);
    size_t _indentLevel;
    vespalib::string _nodeName;
    void indent();
public:
    static vespalib::string dump(const TensorFunction &root);

    void node(const vespalib::string &name);
    void child(const vespalib::string &name, const TensorFunction &child);

    using map_fun_t = double (*)(double);
    using join_fun_t = double (*)(double, double);

    class Arg {
    private:
        DumpTargetBackend &_back_end;
    public:
        Arg(DumpTargetBackend &back_end);
        void value(bool v);
        void value(size_t v);
        void value(map_fun_t v);
        void value(join_fun_t v);
        void value(const vespalib::string &v);
        void value(const std::vector<vespalib::string> &v);
        void value(const Aggr &aggr);
    };

    Arg arg(const vespalib::string &name);

    ~DumpTarget();
};

} // namespace vespalib::eval
} // namespace vespalib
