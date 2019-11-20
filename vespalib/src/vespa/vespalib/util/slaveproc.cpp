// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "guard.h"
#include "slaveproc.h"
#include <cstring>

namespace vespalib {

namespace slaveproc {

using namespace std::chrono;

/**
 * @brief SlaveProc internal timeout management.
 **/
class Timer
{
private:
    const steady_clock::time_point _startTime;
    const int64_t                  _maxTimeMS;
    milliseconds                   _elapsed;

public:
    Timer(int64_t maxTimeMS)
        : _startTime(steady_clock::now()),
          _maxTimeMS(maxTimeMS),
          _elapsed(0)
    { }
    Timer &update() {
        _elapsed = duration_cast<milliseconds>(steady_clock::now() - _startTime);
        return *this;
    }
    int64_t elapsed() const {
        return _elapsed.count();
    }
    int64_t remaining() const {
        if (_maxTimeMS == -1) {
            return -1;
        }
        if (elapsed() > _maxTimeMS) {
            return 0;
        }
        return (_maxTimeMS - _elapsed.count());
    }
    int64_t waitTime() const {
        int res = remaining();
        if (res >= 0 && res <= 10000) {
            return res;
        }
        return 10000;
    }
    bool timeOut() const {
        return (remaining() == 0);
    }
};

} // namespace slaveproc

using slaveproc::Timer;

//-----------------------------------------------------------------------------

void
SlaveProc::Reader::OnReceiveData(const void *data, size_t length)
{
    const char *buf = (const char *) data;
    MonitorGuard lock(_cond);
    if (_gotEOF || (buf != NULL && length == 0)) { // ignore special cases
        return;
    }
    if (buf == NULL) { // EOF
        _gotEOF = true;
    } else {
        _queue.push(std::string(buf, length));
    }
    if (_waitCnt > 0) {
        lock.signal();
    }
}


bool
SlaveProc::Reader::hasData()
{
    // NB: caller has lock on _cond
    return (!_data.empty() || !_queue.empty());
}


bool
SlaveProc::Reader::waitForData(Timer &timer, MonitorGuard &lock)
{
    // NB: caller has lock on _cond
    CounterGuard count(_waitCnt);
    while (!timer.update().timeOut() && !hasData() && !_gotEOF) {
        lock.wait(timer.waitTime());
    }
    return hasData();
}


void
SlaveProc::Reader::updateEOF()
{
    // NB: caller has lock on _cond
    if (_data.empty() && _queue.empty() && _gotEOF) {
        _readEOF = true;
    }
}


SlaveProc::Reader::Reader()
    : _cond(),
      _queue(),
      _data(),
      _gotEOF(false),
      _waitCnt(0),
      _readEOF(false)
{
}


SlaveProc::Reader::~Reader()
{
}


uint32_t
SlaveProc::Reader::read(char *buf, uint32_t len, int msTimeout)
{
    if (eof()) {
        return 0;
    }
    Timer timer(msTimeout);
    MonitorGuard lock(_cond);
    waitForData(timer, lock);
    uint32_t bytes = 0;
    while (bytes < len && hasData()) {
        if (_data.empty()) {
            _data = _queue.front();
            _queue.pop();
        }
        if (len - bytes < _data.length()) {
            memcpy(buf + bytes, _data.data(), len - bytes);
            _data.erase(0, len - bytes);
            bytes = len;
        } else {
            memcpy(buf + bytes, _data.data(), _data.length());
            bytes += _data.length();
            _data.clear();
        }
    }
    updateEOF();
    return bytes;
}


bool
SlaveProc::Reader::readLine(std::string &line, int msTimeout)
{
    line.clear();
    if (eof()) {
        return false;
    }
    Timer timer(msTimeout);
    MonitorGuard lock(_cond);
    while (waitForData(timer, lock)) {
        while (hasData()) {
            if (_data.empty()) {
                _data = _queue.front();
                _queue.pop();
            }
            std::string::size_type ofs = _data.find('\n');
            if (ofs == std::string::npos) {
                line.append(_data);
                _data.clear();
            } else {
                line.append(_data, 0, ofs);
                _data.erase(0, ofs + 1);
                updateEOF();
                return true;
            }
        }
    }
    updateEOF();
    if (eof()) {
        return !line.empty();
    }
    _data.swap(line);
    return false;
}

//-----------------------------------------------------------------------------

void
SlaveProc::checkProc()
{
    if (_running) {
        bool stillRunning;
        if (_proc.PollWait(&_exitCode, &stillRunning) && !stillRunning) {
            _running = false;
            _failed = (_exitCode != 0);
        }
    }
}


SlaveProc::SlaveProc(const char *cmd)
    : _reader(),
      _proc(cmd, true, &_reader),
      _running(false),
      _failed(false),
      _exitCode(-918273645)
{
    _running = _proc.CreateWithShell();
    _failed  = !_running;
}


SlaveProc::~SlaveProc() = default;


bool
SlaveProc::write(const char *buf, uint32_t len)
{
    if (len == 0) {
        return true;
    }
    return _proc.WriteStdin(buf, len);
}


bool
SlaveProc::close()
{
    return _proc.WriteStdin(nullptr, 0);
}


uint32_t
SlaveProc::read(char *buf, uint32_t len, int msTimeout)
{
    return _reader.read(buf, len, msTimeout);
}


bool
SlaveProc::readLine(std::string &line, int msTimeout)
{
    return _reader.readLine(line, msTimeout);
}


bool
SlaveProc::wait(int msTimeout)
{
    bool done = true;
    checkProc();
    if (_running) {
        if (msTimeout != -1) {
            msTimeout = (msTimeout + 999) / 1000;
        }
        if (_proc.Wait(&_exitCode, msTimeout)) {
            _failed = (_exitCode != 0);
        } else {
            _failed = true;
            done = false;
        }
        _running = false;
    }
    return done;
}


bool
SlaveProc::running()
{
    checkProc();
    return _running;
}


bool
SlaveProc::failed()
{
    checkProc();
    return _failed;
}

int
SlaveProc::getExitCode()
{
    return _exitCode;
}


bool
SlaveProc::run(const std::string &input, const char *cmd,
               std::string &output, int msTimeout)
{
    SlaveProc proc(cmd);
    Timer timer(msTimeout);
    char buf[4096];
    proc.write(input.data(), input.length());
    proc.close(); // close stdin
    while (!proc.eof() && !timer.timeOut()) {
        uint32_t res = proc.read(buf, sizeof(buf), timer.remaining());
        output.append(buf, res);
        timer.update();
    }
    if ( ! output.empty() && output.find('\n') == output.size() - 1) {
        output.erase(output.size() - 1, 1);
    }
    proc.wait(timer.update().remaining());
    return (!proc.running() && !proc.failed());
}


bool
SlaveProc::run(const char *cmd, std::string &output, int msTimeout)
{
    std::string input;  // empty input
    return run(input, cmd, output, msTimeout);
}


bool
SlaveProc::run(const char *cmd, int msTimeout)
{
    std::string input;  // empty input
    std::string output; // ignore output
    return run(input, cmd, output, msTimeout);
}

} // namespace vespalib
