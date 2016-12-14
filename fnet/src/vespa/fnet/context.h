// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

class FNET_IOComponent;
class FNET_Connector;
class FNET_Connection;
class FNET_Channel;
class FNET_IExecutable;
/**
 * This class indicates the context of a packet. It is external to the
 * packet class because a single packet may occur in many contexts at
 * the same time (broadcast/multicast).
 **/
class FNET_Context
{
public:
    FNET_Context() : _value()                    { _value.VOIDP   = NULL;  }
    FNET_Context(uint32_t value) : _value()      { _value.INT     = value; }
    FNET_Context(void *value) : _value()         { _value.VOIDP   = value; }
    FNET_Context(FNET_Channel *value) : _value() { _value.CHANNEL = value; }

    FNET_Context(FNET_IOComponent *value) : _value()
    { _value.IOC = value; }
    FNET_Context(FNET_Connector *value) : _value()
    { _value.CONNECTOR = value; }
    FNET_Context(FNET_Connection *value) : _value()
    { _value.CONNECTION = value; }
    FNET_Context(FNET_IExecutable *value) : _value()
    { _value.EXECUTABLE = value; }

    union {
        uint32_t          INT;
        void             *VOIDP;
        FNET_Channel     *CHANNEL;
        FNET_IOComponent *IOC;
        FNET_Connector   *CONNECTOR;
        FNET_Connection  *CONNECTION;
        FNET_IExecutable *EXECUTABLE;
    } _value;

    void Print(uint32_t indent = 0)
    {
        printf("%*sFNET_Context {\n", indent, "");
        printf("%*s  Value[INT]  : %d\n", indent, "", _value.INT);
        printf("%*s  Value[VOIDP]: %p\n", indent, "", _value.VOIDP);
        printf("%*s}\n", indent, "");
    }
};

