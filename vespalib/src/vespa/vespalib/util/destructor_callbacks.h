// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "idestructorcallback.h"

namespace vespalib {

class Gate;

class GateCallback : public IDestructorCallback {
public:
    explicit GateCallback(vespalib::Gate & gate) noexcept : _gate(gate) {}
    ~GateCallback() override;
private:
    vespalib::Gate & _gate;
};

class IgnoreCallback : public IDestructorCallback {
public:
    IgnoreCallback() noexcept { }
    ~IgnoreCallback() override = default;
};

template <typename T>
struct KeepAlive : public IDestructorCallback {
    explicit KeepAlive(T toKeep) noexcept : _toKeep(std::move(toKeep)) { }
    ~KeepAlive() override = default;
    T _toKeep;
};

template<class FunctionType>
class LambdaCallback : public IDestructorCallback {
public:
    explicit LambdaCallback(FunctionType &&func) noexcept
        : _func(std::move(func))
    {}
    ~LambdaCallback() {
        _func();
    }
private:
    FunctionType _func;
};

template<class FunctionType>
std::shared_ptr<IDestructorCallback>
makeLambdaCallback(FunctionType &&function) {
    return std::make_shared<LambdaCallback<std::decay_t<FunctionType>>>
            (std::forward<FunctionType>(function));
}


}
