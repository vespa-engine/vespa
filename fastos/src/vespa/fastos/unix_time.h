// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/fastos/time.h>
#include <vespa/fastos/timestamp.h>


#define FASTOS_UNIX_TIMER_CMP(tvp, uvp, cmp) \
  (((tvp)->tv_sec == (uvp)->tv_sec)? \
   ((tvp)->tv_usec cmp (uvp)->tv_usec) :\
   ((tvp)->tv_sec cmp (uvp)->tv_sec))

#define FASTOS_UNIX_TIMER_SUB(tvp, uvp, vvp) \
	do { \
  	  if ((tvp)->tv_usec >= (uvp)->tv_usec) {\
  	    (vvp)->tv_sec = (tvp)->tv_sec - (uvp)->tv_sec; \
	    (vvp)->tv_usec = (tvp)->tv_usec - (uvp)->tv_usec;\
          } else {\
  	    (vvp)->tv_sec = (tvp)->tv_sec - (uvp)->tv_sec - 1; \
	    (vvp)->tv_usec = (tvp)->tv_usec - (uvp)->tv_usec + 1000000;\
	  }\
        } while (0)

#define FASTOS_UNIX_TIMER_ADD(tvp, uvp, vvp) \
	do { \
  	  if ((tvp)->tv_usec+(uvp)->tv_usec<1000000) {\
  	    (vvp)->tv_sec = (tvp)->tv_sec + (uvp)->tv_sec; \
	    (vvp)->tv_usec = (tvp)->tv_usec + (uvp)->tv_usec;\
          } else {\
  	    (vvp)->tv_sec = (tvp)->tv_sec + (uvp)->tv_sec + 1; \
	    (vvp)->tv_usec = (tvp)->tv_usec + (uvp)->tv_usec - 1000000;\
	  }\
        } while (0)


class FastOS_UNIX_Time : public FastOS_TimeInterface
{
    /*
     * Class for OS independent time to millisecond resolution.
     *
     */
private:
    timeval _time;

public:
    void SetZero() override { _time.tv_sec = 0;  _time.tv_usec = 0; }

    FastOS_UNIX_Time ()
        : _time()
    {
        SetZero();
    }

    FastOS_UNIX_Time (double s)
        : _time()
    {
        SetMilliSecs(s * 1000.0);
    }

    FastOS_UNIX_Time(const FastOS_UNIX_Time &rhs) = default;

    operator fastos::TimeStamp () {
        return fastos::TimeStamp(_time);
    }

    FastOS_UNIX_Time& operator=(const FastOS_UNIX_Time& rhs);
    FastOS_UNIX_Time& operator+=(const FastOS_UNIX_Time& rhs);
    FastOS_UNIX_Time& operator-=(const FastOS_UNIX_Time& rhs);

    bool operator>(const FastOS_UNIX_Time rhs) const {
        return FASTOS_UNIX_TIMER_CMP(&_time, &rhs._time, >);
    }
    bool operator<(const FastOS_UNIX_Time rhs) const {
        return FASTOS_UNIX_TIMER_CMP(&_time, &rhs._time, <);
    }
    bool operator>=(const FastOS_UNIX_Time rhs) const {
        return FASTOS_UNIX_TIMER_CMP(&_time, &rhs._time, >=);
    }
    bool operator<=(const FastOS_UNIX_Time rhs) const {
        return FASTOS_UNIX_TIMER_CMP(&_time, &rhs._time, <=);
    }
    bool operator==(const FastOS_UNIX_Time rhs) const {
        return FASTOS_UNIX_TIMER_CMP(&_time, &rhs._time, ==);
    }

    double MicroSecs() const override;
    double MilliSecs() const override;
    double Secs() const override;

    void SetMicroSecs(double microsecs) override;
    void SetMilliSecs(double millisecs) override;
    void SetSecs(double secs) override;

    void SetNow() override;

    long int GetSeconds() const override { return _time.tv_sec; }
    long int GetMicroSeconds() const override { return _time.tv_usec; }
};
