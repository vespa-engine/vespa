# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.
import time
import errno
from pyroute2.common import ANCIENT
from pyroute2.netlink import NetlinkError
# How long should we wait on EACH commit() checkpoint: for ipaddr,
# ports etc. That's not total commit() timeout.
SYNC_TIMEOUT = 5


class DeprecationException(Exception):
    pass


class CommitException(Exception):
    pass


class CreateException(Exception):
    pass


def bypass(f):
    if ANCIENT:
        return f
    else:
        return staticmethod(lambda *x, **y: None)


class compat(object):
    '''
    A namespace to keep all compat-related methods.
    '''
    @bypass
    @staticmethod
    def fix_timeout(timeout):
        time.sleep(timeout)

    @bypass
    @staticmethod
    def fix_check_link(nl, index):
        # check, if the link really exists --
        # on some old kernels you can receive
        # broadcast RTM_NEWLINK after the link
        # was deleted
        try:
            nl.get_links(index)
        except NetlinkError as e:
            if e.code == errno.ENODEV:  # No such device
                # just drop this message then
                return True
