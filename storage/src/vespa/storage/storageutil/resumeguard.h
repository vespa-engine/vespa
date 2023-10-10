// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace storage {

class ResumeGuard {
public:
    class Callback {
    public:
        virtual ~Callback() {};

        virtual void resume() = 0;
    };

    ResumeGuard()
        : _cb(nullptr)
    {}

    ResumeGuard(Callback& cb)
        : _cb(&cb) {};

    ResumeGuard(const ResumeGuard& other) {
        _cb = other._cb;
        const_cast<ResumeGuard&>(other)._cb = nullptr;
    }

    ~ResumeGuard() {
        if (_cb) {
            _cb->resume();
        }
    }

private:
    Callback* _cb;
};

}

