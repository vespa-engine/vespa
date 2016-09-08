// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "time_box.h"

#if 0

#include <time.h>
#include <unistd.h>
#include <stdio.h>

int main(int, char **)
{
    vespalib::TimeBox tb(3.0, 1.25);
    while (tb.hasTimeLeft()) {
        double tl = tb.timeLeft();
        printf("time left: %g seconds\n", tl);
        sleep(1);
    }
    printf("expired, time left: %g seconds\n", tb.timeLeft());
}

#endif
