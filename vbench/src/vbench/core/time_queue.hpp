// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

namespace vbench {

template <typename T>
TimeQueue<T>::TimeQueue(double window, double tick)
    : _lock(),
      _cond(),
      _time(0.0),
      _window(window),
      _tick(tick),
      _queue(),
      _closed(false)
{
}

template<typename T>
TimeQueue<T>::~TimeQueue() = default;

template <typename T>
void
TimeQueue<T>::close()
{
    std::lock_guard guard(_lock);
    _closed = true;
    _cond.notify_all();
}

template <typename T>
void
TimeQueue<T>::discard()
{
    std::lock_guard guard(_lock);
    while (!_queue.empty()) {
        _queue.pop_any();
    }
    _cond.notify_all();
}

template <typename T>
void
TimeQueue<T>::insert(std::unique_ptr<T> obj, double time)
{
    std::unique_lock guard(_lock);
    while (time > (_time + _window) && !_closed) {
        _cond.wait(guard);
    }
    if (!_closed) {
        _queue.push(Entry(std::move(obj), time));
    }
}

template <typename T>
bool
TimeQueue<T>::extract(double time, std::vector<std::unique_ptr<T> > &list, double &delay)
{
    std::lock_guard guard(_lock);
    _time = time;
    while (!_queue.empty() && _queue.front().time <= time) {
        list.push_back(std::move(_queue.front().object));
        _queue.pop_front();
    }
    _cond.notify_all();
    delay = _queue.empty() ? _tick : (_queue.front().time - time);
    return (!_closed || !_queue.empty() || !list.empty());
}

} // namespace vbench
