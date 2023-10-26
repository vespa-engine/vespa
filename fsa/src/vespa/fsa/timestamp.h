// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    timestamp.h
 * @brief   Simple timestamp class.
 */

#pragma once

#include <sys/time.h>
#include <time.h>

namespace fsa {

// {{{ class TimeStamp

/**
 * @class TimeStamp
 * @brief Simple timestamp class.
 */
class TimeStamp {
private:
  struct timeval _ts;
public:
  /**
   * @brief Constructor, registers current time.
   */
  TimeStamp() {
    gettimeofday(&_ts,NULL);
  }
  /**
   * @brief Destructor.
   */
  ~TimeStamp() {}

  /**
   * @brief Reset timestamp.
   *
   * Set timestamp value to current time.
   */
  void reset()
  {
    gettimeofday(&_ts,NULL);
  }

  /**
   * @brief Get timestamp value (= object creation or last reset time).
   *
   * @return Timestamp value in seconds.
   */
  double getVal() const
  {
    return double(_ts.tv_sec)+double(_ts.tv_usec)/1000000.0;
  }

  /**
   * @brief Get elapsed time (since object creation time).
   *
   * @return Elapsed time in seconds.
   */
  double elapsed() const
  {
    struct timeval now;
    gettimeofday(&now,NULL);
    return double(now.tv_sec)-double(_ts.tv_sec)+
      (double(now.tv_usec)-double(_ts.tv_usec))/1000000.0;
  }

  /**
   * @brief Calculate difference between timestamps.
   *
   * @return Difference between timestamps in seconds.
   */
  double operator-(const TimeStamp &other) const
  {
    return double(_ts.tv_sec)-double(other._ts.tv_sec)+
      (double(_ts.tv_usec)-double(other._ts.tv_usec))/1000000.0;
  }
};

} // namespace fsa

