// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

namespace vbench {

template <typename T>
TimeQueue<T>::TimeQueue(double window, double tick)
    : _monitor(),
      _time(0.0),
      _window(window),
      _tick(tick),
      _queue(),
      _closed(false)
{
}

template <typename T>
void
TimeQueue<T>::close()
{
    vespalib::MonitorGuard guard(_monitor);
    _closed = true;
    guard.broadcast();
}

template <typename T>
void
TimeQueue<T>::discard()
{
    vespalib::MonitorGuard guard(_monitor);
    while (!_queue.empty()) {
        _queue.pop_any();
    }
    guard.broadcast();
}

template <typename T>
void
TimeQueue<T>::insert(std::unique_ptr<T> obj, double time)
{
    vespalib::MonitorGuard guard(_monitor);
    while (time > (_time + _window) && !_closed) {
        guard.wait();
    }
    if (!_closed) {
        _queue.push(Entry(std::move(obj), time));
    }
}

template <typename T>
bool
TimeQueue<T>::extract(double time, std::vector<std::unique_ptr<T> > &list, double &delay)
{
    vespalib::MonitorGuard guard(_monitor);
    _time = time;
    while (!_queue.empty() && _queue.front().time <= time) {
        list.push_back(std::move(_queue.front().object));
        _queue.pop_front();
    }
    guard.broadcast();
    delay = _queue.empty() ? _tick : (_queue.front().time - time);
    return (!_closed || !_queue.empty() || !list.empty());
}

} // namespace vbench
