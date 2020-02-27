// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threading_service_observer.h"

namespace proton::test {

ThreadingServiceObserver::ThreadingServiceObserver(searchcorespi::index::IThreadingService &service)
    : _service(service),
      _master(_service.master()),
      _index(service.index()),
      _summary(service.summary()),
      _shared(service.shared()),
      _indexFieldInverter(_service.indexFieldInverter()),
      _indexFieldWriter(_service.indexFieldWriter()),
      _attributeFieldWriter(_service.attributeFieldWriter())
{
}

ThreadingServiceObserver::~ThreadingServiceObserver() = default;

}
