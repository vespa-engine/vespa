# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.
from pyroute2.netlink import nlmsg


class errmsg(nlmsg):
    '''
    Custom message type

    Error ersatz-message
    '''
    fields = (('code', 'i'), )
