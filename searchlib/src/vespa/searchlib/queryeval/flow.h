// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once
#include <cstddef>

namespace search::queryeval {

// Model how boolean result decisions flow through intermediate nodes
// of different types based on relative estimates for sub-expressions

class AndFlow {
private:
    double _flow;
    size_t _cnt;
public:
    AndFlow(double in = 1.0) noexcept : _flow(in), _cnt(0) {}
    void add(double est) noexcept {
        _flow *= est;
        ++_cnt;
    }
    double flow() const noexcept {
        return _flow;
    }
    double estimate() const noexcept {
        return (_cnt > 0) ? _flow : 0.0;
    }
};

class OrFlow {
private:
    double _flow;
public:
    OrFlow(double in = 1.0) noexcept : _flow(in) {}
    void add(double est) noexcept {
        _flow *= (1.0 - est);
    }
    double flow() const noexcept {
        return _flow;
    }
    double estimate() const noexcept {
        return (1.0 - _flow);
    }
};

class AndNotFlow {
private:
    double _flow;
    size_t _cnt;
public:
    AndNotFlow(double in = 1.0) noexcept : _flow(in), _cnt(0) {}
    void add(double est) noexcept {
        _flow *= (_cnt++ == 0) ? est : (1.0 - est);
    }
    double flow() const noexcept {
        return _flow;
    }
    double estimate() const noexcept {
        return (_cnt > 0) ? _flow : 0.0;
    }
};

}
