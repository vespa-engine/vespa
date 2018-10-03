// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/component/vtag.h>

// DEPRECATED

#define DEPRECATED __attribute__((deprecated))

// FORWARD DECLARATIONS

class FNET_IPacketFactory;
class FNET_IPacketHandler;
class FNET_IPacketStreamer;
class FNET_IServerAdapter;
class FNET_IExecutable;

class FNET_Channel;
class FNET_ChannelLookup;
class FNET_ChannelPool;
class FNET_Config;
class FNET_Connection;
class FNET_Connector;
class FNET_Context;
class FNET_ControlPacket;
class FNET_DataBuffer;
class FNET_DummyPacket;
class FNET_Info;
class FNET_IOComponent;
class FNET_Packet;
class FNET_PacketQueue;
class FNET_Scheduler;
class FNET_SimplePacketStreamer;
class FNET_Task;
class FNET_Transport;
class FNET_TransportThread;

// CONTEXT CLASS (union of types)
#include "context.h"

// INTERFACES
#include "ipacketfactory.h"
#include "ipackethandler.h"
#include "ipacketstreamer.h"
#include "iserveradapter.h"
#include "iexecutable.h"

// CLASSES
#include "task.h"
#include "scheduler.h"
#include "config.h"
#include "databuffer.h"
#include "packet.h"
#include "dummypacket.h"
#include "controlpacket.h"
#include "packetqueue.h"
#include "channel.h"
#include "channellookup.h"
#include "simplepacketstreamer.h"
#include "transport_thread.h"
#include "iocomponent.h"
#include "transport.h"
#include "connection.h"
#include "connector.h"
#include "info.h"
#include "signalshutdown.h"

