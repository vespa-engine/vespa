// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <chrono>
#include <memory>

/**
 * This class is used to mesure time intervals, or time spans. In addition to
 * simply measuring timespans, this class also has the ability to set
 * a maximum timespan and use this as a reference when handling
 * measured time spans. The max time span may be thought of as an
 * upper limit for the time spans you are going to measure. After
 * measuring a time span you may use the @ref GetRemaining and @ref
 * GetOvertime methods to check how the measured time span relates to
 * the maximum time span.
 **/
class Timer
{
private:
  using clock = std::chrono::steady_clock;
  using time_point = std::chrono::time_point<clock>;
  time_point _time;
  double     _timespan;
  double     _maxTime;
  bool       _running;

public:
  using UP = std::unique_ptr<Timer>;
  /**
   * Create a new timer.
   **/
  Timer();

  /**
   * Set the maximum time span.
   *
   * @param max the maximum time span in ms.
   **/
  void SetMax(double max);

  /**
   * Start the timer. This will set the start time to the current
   * time.
   **/
  void Start();

  /**
   * Stop the timer. This will set the measured time span to the
   * difference between the current time and the start time.
   **/
  void Stop();

  /**
   * Set the measured time spen to 0 ms and stop the timer if it is
   * running.
   **/
  void Clear();

  /**
   * Get the measured time span. If the timer is running, @ref Stop
   * will be called.
   *
   * @return the measured time span in ms.
   **/
  double GetTimespan();

  /**
   * Compare the measured time span with the maximum time span. If the
   * maximum time span is greater, the difference between the maximum
   * time span and the measured time span is returned. If the measured
   * time span is greater, 0 is returned as there is no time remaining.
   *
   * @return remaining time in ms, or 0 if no time is remaining.
   **/
  double GetRemaining();

  /**
   * @return time from start to current in ms
   **/
  double GetCurrent();

  /**
   * Static method performing simple testing on the timer class. This
   * method produces output to stdout that needs manual inspection.
   **/
  static void TestClass();
};

