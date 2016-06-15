# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.

from pyroute2.netlink import nlmsg
from pyroute2.netlink import nla


class rtmsg(nlmsg):
    '''
    Route message

    C structure::

        struct rtmsg {
            unsigned char rtm_family;   /* Address family of route */
            unsigned char rtm_dst_len;  /* Length of destination */
            unsigned char rtm_src_len;  /* Length of source */
            unsigned char rtm_tos;      /* TOS filter */

            unsigned char rtm_table;    /* Routing table ID */
            unsigned char rtm_protocol; /* Routing protocol; see below */
            unsigned char rtm_scope;    /* See below */
            unsigned char rtm_type;     /* See below */

            unsigned int  rtm_flags;
        };
    '''
    prefix = 'RTA_'

    fields = (('family', 'B'),
              ('dst_len', 'B'),
              ('src_len', 'B'),
              ('tos', 'B'),
              ('table', 'B'),
              ('proto', 'B'),
              ('scope', 'B'),
              ('type', 'B'),
              ('flags', 'I'))

    nla_map = (('RTA_UNSPEC', 'none'),
               ('RTA_DST', 'ipaddr'),
               ('RTA_SRC', 'ipaddr'),
               ('RTA_IIF', 'uint32'),
               ('RTA_OIF', 'uint32'),
               ('RTA_GATEWAY', 'ipaddr'),
               ('RTA_PRIORITY', 'uint32'),
               ('RTA_PREFSRC', 'ipaddr'),
               ('RTA_METRICS', 'metrics'),
               ('RTA_MULTIPATH', 'hex'),
               ('RTA_PROTOINFO', 'uint32'),
               ('RTA_FLOW', 'hex'),
               ('RTA_CACHEINFO', 'cacheinfo'),
               ('RTA_SESSION', 'hex'),
               ('RTA_MP_ALGO', 'hex'),
               ('RTA_TABLE', 'uint32'),
               ('RTA_MARK', 'uint32'),
               ('RTA_MFC_STATS', 'rta_mfc_stats'))

    class rta_mfc_stats(nla):
        fields = (('mfcs_packets', 'uint64'),
                  ('mfcs_bytes', 'uint64'),
                  ('mfcs_wrong_if', 'uint64'))

    class metrics(nla):
        prefix = 'RTAX_'
        nla_map = (('RTAX_UNSPEC', 'none'),
                   ('RTAX_LOCK', 'uint32'),
                   ('RTAX_MTU', 'uint32'),
                   ('RTAX_WINDOW', 'uint32'),
                   ('RTAX_RTT', 'uint32'),
                   ('RTAX_RTTVAR', 'uint32'),
                   ('RTAX_SSTHRESH', 'uint32'),
                   ('RTAX_CWND', 'uint32'),
                   ('RTAX_ADVMSS', 'uint32'),
                   ('RTAX_REORDERING', 'uint32'),
                   ('RTAX_HOPLIMIT', 'uint32'),
                   ('RTAX_INITCWND', 'uint32'),
                   ('RTAX_FEATURES', 'uint32'),
                   ('RTAX_RTO_MIN', 'uint32'),
                   ('RTAX_INITRWND', 'uint32'),
                   ('RTAX_QUICKACK', 'uint32'))

    class cacheinfo(nla):
        fields = (('rta_clntref', 'I'),
                  ('rta_lastuse', 'I'),
                  ('rta_expires', 'i'),
                  ('rta_error', 'I'),
                  ('rta_used', 'I'),
                  ('rta_id', 'I'),
                  ('rta_ts', 'I'),
                  ('rta_tsage', 'I'))
