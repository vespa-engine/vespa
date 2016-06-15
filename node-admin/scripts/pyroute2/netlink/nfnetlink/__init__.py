# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.
'''
Nfnetlink
=========

The support of nfnetlink families is now at the
very beginning. So there is no public exports
yet, but you can review the code. Work is in
progress, stay tuned.

nf-queue
++++++++

Netfilter protocol for NFQUEUE iptables target.
'''

from pyroute2.netlink import nlmsg


NFNL_SUBSYS_NONE = 0
NFNL_SUBSYS_CTNETLINK = 1
NFNL_SUBSYS_CTNETLINK_EXP = 2
NFNL_SUBSYS_QUEUE = 3
NFNL_SUBSYS_ULOG = 4
NFNL_SUBSYS_OSF = 5
NFNL_SUBSYS_IPSET = 6
NFNL_SUBSYS_COUNT = 7


class nfgen_msg(nlmsg):
    fields = (('nfgen_family', 'B'),
              ('version', 'B'),
              ('res_id', 'H'))
