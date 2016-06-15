# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.

from pyroute2.netlink import nlmsg
from pyroute2.netlink import nla


class ndmsg(nlmsg):
    '''
    ARP cache update message

    C structure::

        struct ndmsg {
            unsigned char ndm_family;
            int           ndm_ifindex;  /* Interface index */
            __u16         ndm_state;    /* State */
            __u8          ndm_flags;    /* Flags */
            __u8          ndm_type;
        };

    Cache info structure::

        struct nda_cacheinfo {
            __u32         ndm_confirmed;
            __u32         ndm_used;
            __u32         ndm_updated;
            __u32         ndm_refcnt;
        };
    '''
    fields = (('family', 'B'),
              ('__pad', '3x'),
              ('ifindex', 'i'),
              ('state', 'H'),
              ('flags', 'B'),
              ('ndm_type', 'B'))

    # Please note, that nla_map creates implicit
    # enumeration. In this case it will be:
    #
    # NDA_UNSPEC = 0
    # NDA_DST = 1
    # NDA_LLADDR = 2
    # NDA_CACHEINFO = 3
    # NDA_PROBES = 4
    # ...
    #
    nla_map = (('NDA_UNSPEC', 'none'),
               ('NDA_DST', 'ipaddr'),
               ('NDA_LLADDR', 'l2addr'),
               ('NDA_CACHEINFO', 'cacheinfo'),
               ('NDA_PROBES', 'uint32'),
               ('NDA_VLAN', 'uint16'),
               ('NDA_PORT', 'be16'),
               ('NDA_VNI', 'be32'),
               ('NDA_IFINDEX', 'uint32'))

    class cacheinfo(nla):
        fields = (('ndm_confirmed', 'I'),
                  ('ndm_used', 'I'),
                  ('ndm_updated', 'I'),
                  ('ndm_refcnt', 'I'))
