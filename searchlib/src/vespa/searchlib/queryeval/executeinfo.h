// Copyright 2019 Oath inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::queryeval {

    /**
     * Holds information about how query will be executed and how large part of corpus will pass through.
     * @author baldersheim
     */
class ExecuteInfo {
public:
    ExecuteInfo(bool strict, float hitRate_in)
        : _hitRate(hitRate_in),
          _strict(strict)
    { }
    bool isStrict() const { return _strict; }
    float hitRate() const { return _hitRate; }
    static const ExecuteInfo TRUE;
    static const ExecuteInfo FALSE;
    static ExecuteInfo create(bool strict);
private:
    float _hitRate;
    bool  _strict;
};

}
