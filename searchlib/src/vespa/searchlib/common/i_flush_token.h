// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

namespace search {

/*
 * Interface class for checking if current flush task should be stopped.
 * TODO: Add methods to register transient memory usage during flush.
 */
class IFlushToken {
public:
    virtual ~IFlushToken() = default;
    virtual bool stop_requested() const noexcept = 0;
};

}
