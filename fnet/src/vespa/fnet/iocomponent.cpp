// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "iocomponent.h"

#include "transport_thread.h"

#include <cassert>
#include <cstring>

FNET_IOComponent::FNET_IOComponent(FNET_TransportThread* owner, int socket_fd, const char* spec, bool shouldTimeOut)
    : _ioc_next(nullptr),
      _ioc_prev(nullptr),
      _ioc_owner(owner),
      _ioc_selector(nullptr),
      _ioc_spec(spec),
      _flags(shouldTimeOut),
      _ioc_socket_fd(socket_fd),
      _ioc_timestamp(vespalib::steady_clock::now()),
      _ioc_lock(),
      _ioc_cond() {}

FNET_IOComponent::~FNET_IOComponent() { assert(_ioc_selector == nullptr); }

const FNET_Config& FNET_IOComponent::getConfig() const { return _ioc_owner->getConfig(); }

void FNET_IOComponent::UpdateTimeOut() { _ioc_owner->UpdateTimeOut(this); }

void FNET_IOComponent::attach_selector(Selector& selector) {
    detach_selector();
    _ioc_selector = &selector;
    _ioc_selector->add(_ioc_socket_fd, *this, _flags._ioc_readEnabled, _flags._ioc_writeEnabled);
}

void FNET_IOComponent::detach_selector() {
    if (_ioc_selector != nullptr) {
        _ioc_selector->remove(_ioc_socket_fd);
    }
    _ioc_selector = nullptr;
}

void FNET_IOComponent::EnableReadEvent(bool enabled) {
    _flags._ioc_readEnabled = enabled;
    if (_ioc_selector != nullptr) {
        _ioc_selector->update(_ioc_socket_fd, *this, _flags._ioc_readEnabled, _flags._ioc_writeEnabled);
    }
}

void FNET_IOComponent::EnableWriteEvent(bool enabled) {
    _flags._ioc_writeEnabled = enabled;
    if (_ioc_selector != nullptr) {
        _ioc_selector->update(_ioc_socket_fd, *this, _flags._ioc_readEnabled, _flags._ioc_writeEnabled);
    }
}

bool FNET_IOComponent::handle_add_event() { return true; }

bool FNET_IOComponent::handle_handshake_act() { return true; }
