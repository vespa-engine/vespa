// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "string.h"

namespace vbench {

/**
 * A Taint indicates whether something has gone wrong. It may also
 * contain a textual reason.
 **/
class Taint
{
private:
    bool   _taint;
    string _reason;

public:
    Taint() : _taint(false), _reason() {}
    Taint(const string &r) : _taint(true), _reason(r) {}
    void reset() { _taint = false; _reason.clear(); }
    void reset(const string &r) { _taint = true; _reason = r; }
    bool taint() const { return _taint; }
    const string &reason() const { return _reason; }
    operator bool() const { return taint(); }
};

} // namespace vbench

