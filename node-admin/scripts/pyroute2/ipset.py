# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.
'''
IPSet module
============

The very basic ipset support.

Right now it is tested only for hash:ip and doesn't support
many useful options. But it can be easily extended, so you
are welcome to help with that.
'''
import socket
from pyroute2.netlink import NLMSG_ERROR
from pyroute2.netlink import NLM_F_REQUEST
from pyroute2.netlink import NLM_F_DUMP
from pyroute2.netlink import NLM_F_ACK
from pyroute2.netlink import NLM_F_EXCL
from pyroute2.netlink import NETLINK_NETFILTER
from pyroute2.netlink.nlsocket import NetlinkSocket
from pyroute2.netlink.nfnetlink import NFNL_SUBSYS_IPSET
from pyroute2.netlink.nfnetlink.ipset import IPSET_CMD_PROTOCOL
from pyroute2.netlink.nfnetlink.ipset import IPSET_CMD_CREATE
from pyroute2.netlink.nfnetlink.ipset import IPSET_CMD_DESTROY
from pyroute2.netlink.nfnetlink.ipset import IPSET_CMD_SWAP
from pyroute2.netlink.nfnetlink.ipset import IPSET_CMD_LIST
from pyroute2.netlink.nfnetlink.ipset import IPSET_CMD_ADD
from pyroute2.netlink.nfnetlink.ipset import IPSET_CMD_DEL
from pyroute2.netlink.nfnetlink.ipset import ipset_msg


def _nlmsg_error(msg):
    return msg['header']['type'] == NLMSG_ERROR


class IPSet(NetlinkSocket):
    '''
    NFNetlink socket (family=NETLINK_NETFILTER).

    Implements API to the ipset functionality.
    '''

    policy = {IPSET_CMD_PROTOCOL: ipset_msg,
              IPSET_CMD_LIST: ipset_msg}

    def __init__(self, version=6, attr_revision=2, nfgen_family=2):
        super(IPSet, self).__init__(family=NETLINK_NETFILTER)
        policy = dict([(x | (NFNL_SUBSYS_IPSET << 8), y)
                       for (x, y) in self.policy.items()])
        self.register_policy(policy)
        self._proto_version = version
        self._attr_revision = attr_revision
        self._nfgen_family = nfgen_family

    def request(self, msg, msg_type,
                msg_flags=NLM_F_REQUEST | NLM_F_DUMP,
                terminate=None):
        msg['nfgen_family'] = self._nfgen_family
        return self.nlm_request(msg,
                                msg_type | (NFNL_SUBSYS_IPSET << 8),
                                msg_flags, terminate=terminate)

    def list(self, name=None):
        '''
        List installed ipsets. If `name` is provided, list
        the named ipset or return an empty list.

        It looks like nfnetlink doesn't return an error,
        when requested ipset doesn't exist.
        '''
        msg = ipset_msg()
        msg['attrs'] = [['IPSET_ATTR_PROTOCOL', self._proto_version]]
        if name is not None:
            msg['attrs'].append(['IPSET_ATTR_SETNAME', name])
        return self.request(msg, IPSET_CMD_LIST)

    def destroy(self, name):
        '''
        Destroy an ipset
        '''
        msg = ipset_msg()
        msg['attrs'] = [['IPSET_ATTR_PROTOCOL', self._proto_version],
                        ['IPSET_ATTR_SETNAME', name]]
        return self.request(msg, IPSET_CMD_DESTROY,
                            msg_flags=NLM_F_REQUEST | NLM_F_ACK | NLM_F_EXCL,
                            terminate=_nlmsg_error)

    def create(self, name, stype='hash:ip', family=socket.AF_INET,
               exclusive=True):
        '''
        Create an ipset `name` of type `stype`, by default
        `hash:ip`.

        Very simple and stupid method, should be extended
        to support ipset options.
        '''
        excl_flag = NLM_F_EXCL if exclusive else 0
        msg = ipset_msg()
        msg['attrs'] = [['IPSET_ATTR_PROTOCOL', self._proto_version],
                        ['IPSET_ATTR_SETNAME', name],
                        ['IPSET_ATTR_TYPENAME', stype],
                        ['IPSET_ATTR_FAMILY', family],
                        ['IPSET_ATTR_REVISION', self._attr_revision]]

        return self.request(msg, IPSET_CMD_CREATE,
                            msg_flags=NLM_F_REQUEST | NLM_F_ACK | excl_flag,
                            terminate=_nlmsg_error)

    def _add_delete(self, name, entry, family, cmd, exclusive):
        if family == socket.AF_INET:
            entry_type = 'IPSET_ATTR_IPADDR_IPV4'
        elif family == socket.AF_INET6:
            entry_type = 'IPSET_ATTR_IPADDR_IPV6'
        else:
            raise TypeError('unknown family')
        excl_flag = NLM_F_EXCL if exclusive else 0

        msg = ipset_msg()
        msg['attrs'] = [['IPSET_ATTR_PROTOCOL', self._proto_version],
                        ['IPSET_ATTR_SETNAME', name],
                        ['IPSET_ATTR_DATA',
                         {'attrs': [['IPSET_ATTR_IP',
                                     {'attrs': [[entry_type, entry]]}]]}]]
        return self.request(msg, cmd,
                            msg_flags=NLM_F_REQUEST | NLM_F_ACK | excl_flag,
                            terminate=_nlmsg_error)

    def add(self, name, entry, family=socket.AF_INET, exclusive=True):
        '''
        Add a member to the ipset
        '''
        return self._add_delete(name, entry, family, IPSET_CMD_ADD, exclusive)

    def delete(self, name, entry, family=socket.AF_INET, exclusive=True):
        '''
        Delete a member from the ipset
        '''
        return self._add_delete(name, entry, family, IPSET_CMD_DEL, exclusive)

    def swap(self, set_a, set_b):
        '''
        Swap two ipsets
        '''
        msg = ipset_msg()
        msg['attrs'] = [['IPSET_ATTR_PROTOCOL', self._proto_version],
                        ['IPSET_ATTR_SETNAME', set_a],
                        ['IPSET_ATTR_TYPENAME', set_b]]
        return self.request(msg, IPSET_CMD_SWAP,
                            msg_flags=NLM_F_REQUEST | NLM_F_ACK,
                            terminate=_nlmsg_error)
