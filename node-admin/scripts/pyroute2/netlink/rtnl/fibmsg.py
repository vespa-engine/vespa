# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.

from pyroute2.common import map_namespace
from pyroute2.netlink import nlmsg

FR_ACT_UNSPEC = 0
FR_ACT_TO_TBL = 1
FR_ACT_GOTO = 2
FR_ACT_NOP = 3
FR_ACT_BLACKHOLE = 6
FR_ACT_UNREACHABLE = 7
FR_ACT_PROHIBIT = 8
(FR_ACT_NAMES, FR_ACT_VALUES) = map_namespace('FR_ACT', globals())


class fibmsg(nlmsg):
    '''
    IP rule message

    C structure::

        struct fib_rule_hdr {
            __u8        family;
            __u8        dst_len;
            __u8        src_len;
            __u8        tos;
            __u8        table;
            __u8        res1;   /* reserved */
            __u8        res2;   /* reserved */
            __u8        action;
            __u32       flags;
        };
    '''
    prefix = 'FRA_'

    fields = (('family', 'B'),
              ('dst_len', 'B'),
              ('src_len', 'B'),
              ('tos', 'B'),
              ('table', 'B'),
              ('res1', 'B'),
              ('res2', 'B'),
              ('action', 'B'),
              ('flags', 'I'))

    # fibmsg NLA numbers are not sequential, so
    # give it here explicitly
    nla_map = ((0, 'FRA_UNSPEC', 'none'),
               (1, 'FRA_DST', 'ipaddr'),
               (2, 'FRA_SRC', 'ipaddr'),
               (3, 'FRA_IIFNAME', 'asciiz'),
               (4, 'FRA_GOTO', 'uint32'),
               (6, 'FRA_PRIORITY', 'uint32'),
               (10, 'FRA_FWMARK', 'uint32'),
               (11, 'FRA_FLOW', 'uint32'),
               (13, 'FRA_SUPPRESS_IFGROUP', 'uint32'),
               (14, 'FRA_SUPPRESS_PREFIXLEN', 'uint32'),
               (15, 'FRA_TABLE', 'uint32'),
               (16, 'FRA_FWMASK', 'uint32'),
               (17, 'FRA_OIFNAME', 'asciiz'))
