// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

namespace vespalib {

struct ComponentConfigProducer {
    struct Config {
        std::string name;
        size_t gen;
        std::string msg;
        Config(const std::string &n, size_t g) : name(n), gen(g), msg() {}
        Config(const std::string &n, size_t g, const std::string &m)
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
