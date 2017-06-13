// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

/**
 * Interface to OS time functions.
 */
class FastOS_TimeInterface
{
protected:
    /**
     * Destructor.  No cleanup needed for base class.
     */
    virtual ~FastOS_TimeInterface() { }

public:
    /**
     * Set the time to 0.
     */
     virtual void SetZero() = 0;

    /**
     * Set the time, specified by number of seconds.
     * @param  seconds        Number of seconds.
     */
    FastOS_TimeInterface& operator=(const double seconds)
    {
        SetSecs(seconds);
        return *this;
    }

    /**
     * Return the microsecond difference between the current time
     * and the time stored in the instance.
     * Note: Only millisecond accuracy is guaranteed.
     * @return              Time difference in microseconds.
     */
    double MicroSecsToNow() const;
    /**
     * Return the millisecond difference between the current time
     * and the time stored in the instance.
     * @return              Time difference in milliseconds.
     */
    double MilliSecsToNow() const;

    /**
     * Add a specified number of microseconds to the time.
     * Note: Only millisecond accuracy is guaranteed.
     * @param  microsecs    Number of microseconds to add.
     */
    void AddMicroSecs(double microsecs) { SetMicroSecs(MicroSecs() + microsecs); }

    /**
     * Add a specified number of milliseconds to the time.
     * @param  millisecs    Number of milliseconds to add.
     */
    void AddMilliSecs(double millisecs) { SetMilliSecs(MilliSecs() + millisecs); }

    /**
     * Subtract a specified number of microseconds from the time.
     * Note: Only millisecond accuracy is guaranteed.
     * @param  microsecs    Number of microseconds to subtract.
     */
    void SubtractMicroSecs(double microsecs) { SetMicroSecs(MicroSecs() - microsecs); }

    /**
     * Subtract a specified number of milliseconds from the time.
     * @param  millisecs    Number of milliseconds to subtract.
     */
    void SubtractMilliSecs(double millisecs) { SetMilliSecs(MilliSecs() - millisecs); }

    /**
     * Return the time in microseconds.
     * Note: Only millisecond accuracy is guaranteed.
     * @return              Time in microseconds.
     */
     virtual double MicroSecs() const = 0;

    /**
     * Return the time in milliseconds.
     * @return              Time in milliseconds.
     */
     virtual double MilliSecs() const = 0;

    /**
     * Return the time in seconds.
     * @return              Time in seconds.
     */
     virtual double Secs() const = 0;

    /**
     * Set the time, specified in microseconds.
     * Note: Only millisecond accuracy is guaranteed.
     * @param  microsecs    Time in microseconds.
     */
     virtual void SetMicroSecs(double microsecs) = 0;

    /**
     * Set the time, specified in milliseconds.
     * @param  millisecs    Time in milliseconds.
     */
     virtual void SetMilliSecs(double millisecs) = 0;

    /**
     * Set the time, specified in seconds.
     * @param  secs    Time in seconds.
     */
     virtual void SetSecs(double secs) = 0;

    /**
     * Set the time value to the current system time.
     */
     virtual void SetNow() = 0;

    /**
     * Get the seconds-part of the time value. If the time value
     * is 56.1234, this method will return 56.
     * @return              Number of seconds.
     */
     virtual long int GetSeconds() const = 0;

    /**
     * Get the microsecond-part of the time value. If the time
     * value is 56.123456, this method will return 123456.
     * Note: Only millisecond accuracy is guaranteed.
     * @return              Number of microseconds.
     */
     virtual long int GetMicroSeconds() const = 0;
};

#include <vespa/fastos/unix_time.h>
using FastOS_Time = FastOS_UNIX_Time;

