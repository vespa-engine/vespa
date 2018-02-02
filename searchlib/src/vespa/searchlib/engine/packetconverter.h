// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchrequest.h"
#include "searchreply.h"
#include "docsumrequest.h"
#include "docsumreply.h"
#include "monitorrequest.h"
#include "monitorreply.h"
#include "tracereply.h"
#include <vespa/searchlib/common/packets.h>

namespace search::engine {


/**
 * This class helps convert data back and forth between transport
 * packets and engine api request/reply objects. All converting
 * methods expect the const object to be fully filled out and the
 * non-const object to be newly created and thus empty. Half of the
 * methods are left unimplemented for now as they would only be needed
 * if we also were to use the api to wrap remote engines. However, if
 * such a time comes, we will probably not be using the packet
 * protocol anymore anyways.
 **/
class PacketConverter
{
private:
    PacketConverter(); // can not be instantiated
    PacketConverter(const PacketConverter &);
    PacketConverter &operator=(const PacketConverter &);

public:
    typedef search::fs4transport::FS4Packet_QUERYX         QUERYX;
    typedef search::fs4transport::FS4Packet_QUERYRESULTX   QUERYRESULTX;
    typedef search::fs4transport::FS4Packet_ERROR          ERROR;
    typedef search::fs4transport::FS4Packet_GETDOCSUMSX    GETDOCSUMSX;
    typedef search::fs4transport::FS4Packet_DOCSUM         DOCSUM;
    typedef search::fs4transport::FS4Packet_EOL            EOL;
    typedef search::fs4transport::FS4Packet_MONITORQUERYX  MONITORQUERYX;
    typedef search::fs4transport::FS4Packet_MONITORRESULTX MONITORRESULTX;
    typedef search::fs4transport::FS4Packet_TRACEREPLY     TRACEREPLY;

    /**
     * Utility conversion from a "fef" set of propertymaps to an array of FS4Properties.
     * @return false if no properties were converted.
     **/
    static void
    fillPacketProperties(const PropertiesMap &source, search::fs4transport::PropsVector& target);

    /**
     * Convert from a QUERYX packet to a SearchRequest object.
     *
     * @param packet transport packet
     * @param request api request object
     **/
    static void toSearchRequest(const QUERYX &packet, SearchRequest &request);

    /**
     * Convert from a SearchRequest object to a QUERYX packet.
     *
     * (NOT YET IMPLEMENTED)
     *
     * @param request api request object
     * @param packet transport packet
     **/
    static void fromSearchRequest(const SearchRequest &request, QUERYX &packet);

    /**
     * Convert from a QUERYRESULTX packet to a SearchReply object.
     *
     * (NOT YET IMPLEMENTED)
     *
     * @param packet transport packet
     * @param reply api reply object
     **/
    static void toSearchReply(const QUERYRESULTX &packet, SearchReply &reply);

    /**
     * Convert from a SearchReply object to a QUERYRESULTX
     * packet. Note that this method only handles the query result
     * aspect of the reply, errors and queue length reporting still
     * needs to be handled separately by the code using this utility
     * method.
     *
     * @param reply api reply object
     * @param packet transport packet
     **/
    static void fromSearchReply(const SearchReply &reply, QUERYRESULTX &packet);

    /**
     * Convert from a GETDOCSUMSX packet to a DocsumRequest object.
     *
     * @param packet transport packet
     * @param request api request object
     **/
    static void toDocsumRequest(const GETDOCSUMSX &packet, DocsumRequest &request);

    /**
     * Convert from a DocsumRequest object to a GETDOCSUMSX packet.
     *
     * (NOT YET IMPLEMENTED)
     *
     * @param packet transport packet
     * @param request api request object
     **/
    static void fromDocsumRequest(const DocsumRequest &request, GETDOCSUMSX &packet);

    /**
     * Convert from a DOCSUM packet to an entry in a DocsumReply object
     *
     * (NOT YET IMPLEMENTED)
     *
     * @param packet transport packet
     * @param docsum api reply object element
     **/
    static void toDocsumReplyElement(const DOCSUM &packet, DocsumReply::Docsum &docsum);

    /**
     * Convert from an entry in a DocsumReply object to a DOCSUM packet.
     *
     * @param docsum api reply object element
     * @param packet transport packet
     **/
    static void fromDocsumReplyElement(const DocsumReply::Docsum &docsum, DOCSUM &packet);

    /**
     * Convert a MONITORQUERYX packet to a MonitorRequest object.
     *
     * @param packet transport packet
     * @param request api request object
     **/
    static void toMonitorRequest(const MONITORQUERYX &packet, MonitorRequest &request);

    /**
     * Convert from a MonitorRequest object to a MONITORQUERYX packet
     *
     * (NOT YET IMPLEMENTED)
     *
     * @param request api request object
     * @param packet transport packet
     **/
    static void fromMonitorRequest(const MonitorRequest &request, MONITORQUERYX &packet);

    /**
     * Convert from a MONITORRESULTX packet to a MonitorReply object.
     *
     * (NOT YET IMPLEMENTED)
     *
     * @param packet transport packet
     * @param reply api reply object
     **/
    static void toMonitorReply(const MONITORRESULTX &packet, MonitorReply &reply);

    /**
     * Convert from a MonitorReply object to a MONITORRESULTX packet.
     *
     * @param reply api reply object
     * @param packet transport packet
     **/
    static void fromMonitorReply(const MonitorReply &reply, MONITORRESULTX &packet);

    /**
     * Convert from a TraceReply object to a TRACE packet.
     *
     * @param reply api reply object
     * @param packet transport packet
     **/
    static void fromTraceReply(const TraceReply &reply, TRACEREPLY &packet);
};

}

