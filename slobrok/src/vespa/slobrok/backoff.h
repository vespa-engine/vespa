// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace slobrok {
namespace api {

class BackOff
{
private:
    double _time;
    double _warntime;
    double _nextwarn;

public:
    BackOff() : _time(0.50), _warntime(0.0), _nextwarn(4.0) {}
    void reset() { _time = 0.50; _warntime = 0.0; _nextwarn = 15.0; }
    double get() {
        double ret = _time;
        _warntime += ret;
        if (_time < 5.0) {
            _time += 0.5;
        } else if (_time < 10.0) {
            _time += 1.0;
        } else if (_time < 30.0) {
            _time += 5;
        } else {
            // max retry time is 30 seconds
            _time = 30.0;
        }
        return ret;
    }
    bool shouldWarn() {
        if (_warntime > _nextwarn) {
            _warntime = 0.0;
            _nextwarn *= 4.0;
            if (_nextwarn > 86400.0) {
                _nextwarn = 86400.0;
            }
            return true;
        } else {
            return false;
        }
    }
};

} // namespace api
} // namespace slobrok

