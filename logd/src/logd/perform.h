// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once
namespace logdemon {

class Performer
{
protected:
    LevelParser _levelparser;
public:
    virtual void doCmd(char *line) = 0;
    Performer() : _levelparser() {}
    virtual ~Performer();
};

class ExternalPerformer: public Performer
{
private:
    Forwarder& _forwarder;
    Services&  _services;
    void listStates(const char *service, const char *component);
public:
    void doCmd(char *line) override;
    void doSetAllStates(char *levmods, char * line);
    char *doSetState(char *levmods, Component *cmp, char *line);
    ExternalPerformer(Forwarder& fw, Services& s)
        : _forwarder(fw), _services(s) {}
    ~ExternalPerformer();
};

class InternalPerformer: public Performer
{
    Services&  _services;
public:
    void doCmd(char *line) override;
    InternalPerformer(Services& s) : _services(s) {}
    ~InternalPerformer() {}
};

} // namespace
