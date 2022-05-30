// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {

struct ComponentConfigProducer {
    struct Config {
        vespalib::string name;
        size_t gen;
        vespalib::string msg;
        Config(const vespalib::string &n, size_t g) : name(n), gen(g), msg() {}
        Config(const vespalib::string &n, size_t g, const vespalib::string &m)
            : name(n), gen(g), msg(m) {}
        ~Config();
    };
    struct Consumer {
        virtual void add(const Config &config) = 0;
        virtual ~Consumer() {}
    };
    virtual void getComponentConfig(Consumer &consumer) = 0;
    virtual ~ComponentConfigProducer() {}
};

} // namespace vespalib
