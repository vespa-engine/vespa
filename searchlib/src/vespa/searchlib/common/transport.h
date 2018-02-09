// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::fs4transport {

/**
 * Instead of using a 32-bit number to send the 'usehardware' flag, we
 * now use this 32-bit number to send 32 flags. The currently defined flags
 * are as follows:
 * <ul>
 *  <li><b>QFLAG_EXTENDED_COVERAGE</b>: Indicates that the it is able to receive extended coverage information.</li>
 *  <li><b>QFLAG_COVERAGE_NODES</b>: Indicate that it is able to handle nodes information.</li>
 *  <li><b>QFLAG_ESTIMATE</b>: Indicates that the  query is performed to get
 *                             an estimate of the total number of hits</li>
 *  <li><b>QFLAG_DUMP_FEATURES</b>: Dump detailed ranking information. Note that
 *                             this flag will only be considered when sent in a
 *                             GETDOCSUMSX packet. Is is put here to avoid having
 *                             2 separate query related flag spaces</li>
 *  <li><b>QFLAG_DROP_SORTDATA</b>: Don't return any sort data even if sortspec
 *                             is used.</li>
 *  <li><b>QFLAG_NO_RESULTCACHE</b>: Do not use any result cache. Perform query no matter what.</li>
 * </ul>
 **/
enum queryflags {
    QFLAG_EXTENDED_COVERAGE    = 0x00000001,
    QFLAG_COVERAGE_NODES       = 0x00000002,
    QFLAG_ESTIMATE             = 0x00000080,
    QFLAG_DROP_SORTDATA        = 0x00004000,
    QFLAG_NO_RESULTCACHE       = 0x00010000,
    QFLAG_DUMP_FEATURES        = 0x00040000
};


/**
 * The new PCODE_QUERYRESULTX packet contains a 32-bit field called
 * 'featureflags'. Each bit in that field denotes a separate feature
 * that may be present in the query result packet or not. The comment
 * describing the packet format indicates what data fields depend on
 * what features. The features present in the 'old' query result packets
 * are defined in this enum along with the Query Result Features
 * themselves. The value called QRF_SUPPORTED_MASK denotes which
 * features are supported by the current version. If a packet with
 * unknown features is received on the network is is discarded (as it
 * would be if it had an illegal PCODE).
 **/
enum queryresult_features {
    QRF_MLD                   = 0x00000001,
    QRF_COVERAGE_NODES        = 0x00000002,
    QRF_SORTDATA              = 0x00000010,
    QRF_EXTENDED_COVERAGE     = 0x00000020,
    QRF_COVERAGE              = 0x00000040,
    QRF_GROUPDATA             = 0x00000200,
    QRF_PROPERTIES            = 0x00000400
};


/**
 * The new PCODE_QUERYX packet contains a 32-bit field called
 * 'featureflags'. Each bit in that field denotes a separate feature
 * that may be present in the query packet or not. The comment
 * describing the packet format indicates what data fields depend on
 * what features. The features present in the
 * 'old' query packets are defined in this enum along with the Query
 * Features themselves. The values called
 * QF_SUPPORTED_[FSEARCH/FDISPATCH]_MASK denotes which features are
 * supported by the current version. If a packet with unknown features
 * is received on the network is is discarded (as it would be if it
 * had an illegal PCODE).
 **/
enum query_features {
    QF_PARSEDQUERY            = 0x00000002,
    QF_RANKP                  = 0x00000004,
    QF_SORTSPEC               = 0x00000080,
    QF_LOCATION               = 0x00000800,
    QF_PROPERTIES             = 0x00100000,
    QF_GROUPSPEC              = 0x00400000,
    QF_SESSIONID              = 0x00800000
};


/**
 * The new PCODE_GETDOCSUMSX packet contains a 32-bit field called
 * 'featureflags'. Each bit in that field denotes a separate feature
 * that may be present in the getdocsums packet or not. The comment
 * describing the packet format indicates what data fields depend on
 * what features. The features present in the 'old' getdocsums packets are
 * defined in this enum along with the GetDocsums Features
 * themselves. The values called
 * GDF_SUPPORTED_[FSEARCH/FDISPATCH]_MASK denotes which features are
 * supported by the current version. If a packet with unknown features
 * is received on the network is is discarded (as it would be if it
 * had an illegal PCODE).
 **/
enum getdocsums_features {
    GDF_MLD                   = 0x00000001,
    GDF_QUERYSTACK            = 0x00000004,
    GDF_RANKP_QFLAGS          = 0x00000010,
    GDF_LOCATION              = 0x00000080,
    GDF_RESCLASSNAME          = 0x00000800,
    GDF_PROPERTIES            = 0x00001000,
    GDF_FLAGS                 = 0x00002000
};


enum getdocsums_flags {
    GDFLAG_IGNORE_ROW         = 0x00000001,
    GDFLAG_ALLOW_SLIME_NOTUSED = 0x00000002 // TODO: remove in Vespa 7
};

// docsum class for slime tunneling
const uint32_t SLIME_MAGIC_ID = 0x55555555;

enum monitorquery_features {
    MQF_QFLAGS          = 0x00000002,
};

enum monitorquery_flags {
    MQFLAG_REPORT_ACTIVEDOCS  = 0x00000020
};

enum monitorresult_features {
    MRF_MLD         = 0x00000001,
    MRF_RFLAGS      = 0x00000008,
    MRF_ACTIVEDOCS  = 0x00000010,
};

/**
 * Codes for packets between dispatch nodes and search nodes.
 * general packet (i.e. message) format:
 * uint32_t  packetLength- length in bytes, EXCLUDING this length field
 * packetcode pCode - see the enum below; same length as uint32_t
 * packetData       - variable length
 */
enum packetcode {
    PCODE_EOL = 200,    /* ..fdispatch <-> ..fsearch.   PacketData:
             *0 {uint32_t queryId,} - only in new format!*/
    PCODE_QUERY_NOTUSED = 201,
    PCODE_QUERYRESULT_NOTUSED = 202,
    PCODE_ERROR = 203,          /* ..fdispatch <-  ..fsearch/..fdispatch
                           *    {uint32_t queryId,} - only in new format!
                           *      uint32_t  error_code  [see common/errorcodes.h]
                           *      uint32_t  message_len
                           *      char[]    message     (UTF-8)   */
    PCODE_GETDOCSUMS_NOTUSED = 204,
    PCODE_DOCSUM = 205,     /* ..fdispatch <-  ..fsearch.
                                 *0 {uint32_t queryId,} - only in new format!
                                 *1 uint32_t location
                                 *2 char[] <title, incipit, URL, ...>
                                 */
    PCODE_MONITORQUERY_NOTUSED = 206,
    PCODE_MONITORRESULT_NOTUSED = 207,
    PCODE_MLD_QUERYRESULT_NOTUSED = 208,
    PCODE_MLD_GETDOCSUMS_NOTUSED = 209,
    PCODE_MLD_MONITORRESULT_NOTUSED = 210,
    PCODE_CLEARCACHES_NOTUSED = 211,
    PCODE_QUERY2_NOTUSED = 212,
    PCODE_PARSEDQUERY2_NOTUSED = 213,
    PCODE_MLD_QUERYRESULT2_NOTUSED = 214,
    PCODE_MLD_GETDOCSUMS2_NOTUSED = 215,
    PCODE_QUEUELEN_NOTUSED = 216,

    PCODE_QUERYRESULTX = 217,   /*
             *      {uint32_t queryId,}    - only if persistent
                         *      uint32_t featureflags, - see 'queryresult_features'
             *      uint32_t offset,
             *      uint32_t numDocs,
             *      uint32_t totNumDocs,
             *      search::HitRank maxRank,
             *      uint32_t docstamp,
                         *      uint32_t[numDocs] sortIndex   - if QRF_SORTDATA
                         *      char[sidx[n - 1]] sortData    - if QRF_SORTDATA
                         *      uint32_t           groupDataLen - if QRF_GROUPDATA
                         *      char[groupDataLen] groupData    - if QRF_GROUPDATA
                         *      uint64_t coverageDocs  - if QRF_COVERAGE
                         *      uint32_t coverageNodes - if QRF_COVERAGE
                         *      uint32_t coverageFull  - if QRF_COVERAGE
             *      numDocs * hit {
             *      uint32_t docid,
             *      search::HitRank metric,
                         *          uint32_t partid,   - if QRF_MLD
                         *          uint32_t docstamp, - if QRF_MLD
             *  }                */
    PCODE_QUERYX = 218,         /*
                                 *  {uint32_t queryId,}          - only if persistent
                                 *      uint32_t featureflags,       - see 'query_features'
                                 *  uint32_t querytype
                                 *  uint32_t offset,
                                 *  uint32_t maxhits,
                                 *  uint32_t qflags,
                                 *      uint32_t minhits,            - if QF_MINHITS
                                 *      uint32_t numProperties       - if QF_PROPERTIES
                                 *      numProperties * props {      - if QF_PROPERTIES
                                 *        uint32_t nameLen
                                 *        char[nameLen] name
                                 *        uint32_t numEntries
                                 *        numentries * entry {
                                 *          uint32_t keyLen
                                 *          char[keyLen] key
                                 *          uint32_t valueLen
                                 *          char[valueLen] value
                                 *        }
                                 *      }
                                 *      uint32_t sortSpecLen         - if QF_SORTSPEC
                                 *      char[sortSpecLen] sortSpec   - if QF_SORTSPEC
                                 *      uint32_t groupSpecLen         - if QF_GROUPSPEC
                                 *      char[groupSpecLen] groupSpec  - if QF_GROUPSPEC
                                 *      uint32_t locationLen         - if QF_LOCATION
                                 *      char[locationLen] location   - if QF_LOCATION
                                 *      uint32_t numStackItems,      - if QF_PARSEDQUERY
                                 *  multiple encoded stackitems: - if QF_PARSEDQUERY
                                 - uint32_t OR|AND|NOT|RANK
                                 uint32_t arity
                                 - uint32_t PHRASE
                                 uint32_t arity
                                 uint32_t indexNameLen
                                 char[]   indexName
                                 - uint32_t TERM
                                 uint32_t indexNameLen
                                 char[]   indexName
                                 uint32_t termLen
                                 char[]   term
                                 */
    PCODE_GETDOCSUMSX = 219,    /*
             *      {uint32_t queryId,}           - only if persistent
                         *      uint32_t featureflags,        - see 'getdocsums_features'
             *      uint32_t docstamp,
                         *      uint32_t rankprofile,         - if GDF_RANKP_QFLAGS
                         *      uint32_t qflags,              - if GDF_RANKP_QFLAGS
                         *      uint32_t resClassNameLen      - if GDF_RESCLASSNAME
                         *      char []  resClassName         - if GDF_RESCLASSNAME
                         *      uint32_t numProperties        - if GDF_PROPERTIES
                         *      numProperties * props {       - if GDF_PROPERTIES
                         *        uint32_t nameLen
                         *        char[nameLen] name
                         *        uint32_t numEntries
                         *        numentries * entry {
                         *          uint32_t keyLen
                         *          char[keyLen] key
                         *          uint32_t valueLen
                         *          char[valueLen] value
                         *        }
                         *      }
             *      uint32_t stackItems,          - if GDF_STACKDUMP
             *      uint32_t stackDumpLen,        - if GDF_STACKDUMP
             *      char[stackDumpLen] stackDump, - if GDF_STACKDUMP
                         *      uint32_t locationLen          - if GDF_LOCATION
                         *      char[locationLen] location    - if GDF_LOCATION
             *      N * doc {
             *          uint32_t docid,
             *          uint32_t partid,          - if GDF_MLD
             *          uint32_t docstamp,        - if GDF_MLD
                         *      }
                         */
    PCODE_MONITORQUERYX = 220,  /*
                           *    uint32_t featureFlags;
                           *        - see monitorquery_features
                           */
    PCODE_MONITORRESULTX = 221, /*
                           *    uint32_t featureFlags;
                           *        - see monitorresult_features
                           *    uint32_t partitionId;
                           *    uint32_t timestamp;
                           *    uint32_t totalNodes;          - if MRF_MLD
                           *    uint32_t activeNodes;         - if MRF_MLD
                           *    uint32_t totalParts;          - if MRF_MLD
                           *    uint32_t activeParts;         - if MRF_MLD
                           */
    PCODE_TRACEREPLY = 222,  /*
                         *      numProperties * props {
                         *        uint32_t nameLen
                         *        char[nameLen] name
                         *        uint32_t numEntries
                         *        numentries * entry {
                         *          uint32_t keyLen
                         *          char[keyLen] key
                         *          uint32_t valueLen
                         *          char[valueLen] value
                         *        }
                         *      }
                         */
    PCODE_LastCode = 223    // Used for consistency checking only, must be last.
};

}
