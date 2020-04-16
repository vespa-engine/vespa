// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

using TimePoint = std::chrono::steady_clock::time_point;
using Duration = std::chrono::steady_clock::duration;

double to_ms(Duration elapsed) {
    std::chrono::duration<double, std::milli> ms(elapsed);
    return ms.count();
}
