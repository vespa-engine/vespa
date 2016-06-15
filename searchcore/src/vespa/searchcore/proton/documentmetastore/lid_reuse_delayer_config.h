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
    bool                             _hasIndexedFields;
public:
    LidReuseDelayerConfig();
    explicit LidReuseDelayerConfig(const DocumentDBConfig &configSnapshot);
    fastos::TimeStamp visibilityDelay() const { return _visibilityDelay; }
    bool hasIndexedFields() const { return _hasIndexedFields; }
};

} // namespace proton::documentmetastore

} // namespace proton
