// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threading_service_observer.h"

namespace proton::test {

ThreadingServiceObserver::ThreadingServiceObserver(searchcorespi::index::IThreadingService &service)
    : _service(service),
      _master(_service.master()),
      _index(service.index()),
      _summary(service.summary()),
      _shared(service.shared()),
      _field_writer(_service.field_writer())
{
}

ThreadingServiceObserver::~ThreadingServiceObserver() = default;

}
