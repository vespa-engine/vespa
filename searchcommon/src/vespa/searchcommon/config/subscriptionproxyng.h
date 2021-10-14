// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/helper/legacysubscriber.h>
#include <vespa/vespalib/stllike/string.h>

namespace search {

template <typename ME, typename CFG>
class SubscriptionProxyNg : public config::IFetcherCallback<CFG>
{
    typedef void (ME::*Method)(const CFG &cfg);

private:
    ME                       &_target;
    Method                    _method;
    config::LegacySubscriber *_subscriber;
    vespalib::string          _cfgId;

    SubscriptionProxyNg(const SubscriptionProxyNg&);
    SubscriptionProxyNg &operator=(const SubscriptionProxyNg&);

public:
    SubscriptionProxyNg(ME &target, Method method)
        : _target(target),
          _method(method),
          _subscriber(NULL),
          _cfgId("")
    {
    }
    virtual ~SubscriptionProxyNg() {
        unsubscribe();
    }
    const char *getConfigId() const {
        return _cfgId.c_str();
    }
    void subscribe(const char *configId) {
        if (_subscriber != NULL) {
            if (configId != NULL && strcmp(configId, _subscriber->id().c_str()) == 0)
            {
                return; // same id; ignore
            } else {
                unsubscribe();
            }
        }
        if (configId != NULL && configId[0] != '\0') {
            _cfgId = configId;
            _subscriber = new config::LegacySubscriber();
            _subscriber->subscribe<CFG>(configId, this);
        }
    }
    void unsubscribe() {
        delete _subscriber;
        _subscriber = NULL;
        _cfgId = "";
    }
    virtual void configure(std::unique_ptr<CFG> cfg) override {
        (_target.*_method)(*cfg);
    }
};

} // namespace search

