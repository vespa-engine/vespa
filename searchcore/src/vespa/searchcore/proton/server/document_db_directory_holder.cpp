// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_db_directory_holder.h"
#include <mutex>
#include <condition_variable>

namespace proton {

namespace {

std::mutex mutex;
std::condition_variable cv;

}

DocumentDBDirectoryHolder::DocumentDBDirectoryHolder()
{
}

DocumentDBDirectoryHolder::~DocumentDBDirectoryHolder()
{
    std::lock_guard guard(mutex);
    cv.notify_all();
}

void
DocumentDBDirectoryHolder::waitUntilDestroyed(const std::weak_ptr<DocumentDBDirectoryHolder> &holder)
{
    std::unique_lock guard(mutex);
    cv.wait(guard, [&]() { return !holder.lock(); });
}

} // namespace proton
