// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "i_flush_token.h"
#include <atomic>

namespace search {

/*
 * Class for checking if current flush task should be stopped.
 */
class FlushToken : public IFlushToken {
    std::atomic<bool> _stop;
public:
    FlushToken();
    ~FlushToken() override;
    bool stop_requested() const noexcept override;
    void request_stop() noexcept;
};

}
