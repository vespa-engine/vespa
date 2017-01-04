// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton
{

class DocumentDBConfig;

namespace documentmetastore
{

/*
 * Class representing configuration for lid reuse delayer.
 */
class LidReuseDelayerConfig
{
private:
    fastos::TimeStamp                _visibilityDelay;
    bool                             _hasIndexedOrAttributeFields;
public:
    LidReuseDelayerConfig();
    explicit LidReuseDelayerConfig(const DocumentDBConfig &configSnapshot);
    fastos::TimeStamp visibilityDelay() const { return _visibilityDelay; }
    bool hasIndexedOrAttributeFields() const { return _hasIndexedOrAttributeFields; }
};

} // namespace proton::documentmetastore

} // namespace proton
