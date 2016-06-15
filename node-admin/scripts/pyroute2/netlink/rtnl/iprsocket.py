# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.

from pyroute2.proxy import NetlinkProxy
from pyroute2.common import ANCIENT
from pyroute2.netlink import NETLINK_ROUTE
from pyroute2.netlink.nlsocket import Marshal
from pyroute2.netlink.nlsocket import NetlinkSocket
from pyroute2.netlink import rtnl
from pyroute2.netlink.rtnl.tcmsg import tcmsg
from pyroute2.netlink.rtnl.rtmsg import rtmsg
from pyroute2.netlink.rtnl.ndmsg import ndmsg
from pyroute2.netlink.rtnl.fibmsg import fibmsg
from pyroute2.netlink.rtnl.ifinfmsg import ifinfmsg
from pyroute2.netlink.rtnl.ifinfmsg import proxy_newlink
from pyroute2.netlink.rtnl.ifinfmsg import proxy_setlink
from pyroute2.netlink.rtnl.ifinfmsg import proxy_dellink
from pyroute2.netlink.rtnl.ifinfmsg import proxy_linkinfo
from pyroute2.netlink.rtnl.ifaddrmsg import ifaddrmsg


class MarshalRtnl(Marshal):
    msg_map = {rtnl.RTM_NEWLINK: ifinfmsg,
               rtnl.RTM_DELLINK: ifinfmsg,
               rtnl.RTM_GETLINK: ifinfmsg,
               rtnl.RTM_SETLINK: ifinfmsg,
               rtnl.RTM_NEWADDR: ifaddrmsg,
               rtnl.RTM_DELADDR: ifaddrmsg,
               rtnl.RTM_GETADDR: ifaddrmsg,
               rtnl.RTM_NEWROUTE: rtmsg,
               rtnl.RTM_DELROUTE: rtmsg,
               rtnl.RTM_GETROUTE: rtmsg,
               rtnl.RTM_NEWRULE: fibmsg,
               rtnl.RTM_DELRULE: fibmsg,
               rtnl.RTM_GETRULE: fibmsg,
               rtnl.RTM_NEWNEIGH: ndmsg,
               rtnl.RTM_DELNEIGH: ndmsg,
               rtnl.RTM_GETNEIGH: ndmsg,
               rtnl.RTM_NEWQDISC: tcmsg,
               rtnl.RTM_DELQDISC: tcmsg,
               rtnl.RTM_GETQDISC: tcmsg,
               rtnl.RTM_NEWTCLASS: tcmsg,
               rtnl.RTM_DELTCLASS: tcmsg,
               rtnl.RTM_GETTCLASS: tcmsg,
               rtnl.RTM_NEWTFILTER: tcmsg,
               rtnl.RTM_DELTFILTER: tcmsg,
               rtnl.RTM_GETTFILTER: tcmsg}

    def fix_message(self, msg):
        # FIXME: pls do something with it
        try:
            msg['event'] = rtnl.RTM_VALUES[msg['header']['type']]
        except:
            pass


class IPRSocketMixin(object):

    def __init__(self, fileno=None):
        super(IPRSocketMixin, self).__init__(NETLINK_ROUTE, fileno=fileno)
        self.marshal = MarshalRtnl()
        self.ancient = ANCIENT
        self._s_channel = None
        self._sproxy = NetlinkProxy(policy='return', nl=self)
        self._sproxy.pmap = {rtnl.RTM_NEWLINK: proxy_newlink,
                             rtnl.RTM_SETLINK: proxy_setlink,
                             rtnl.RTM_DELLINK: proxy_dellink}
        self._rproxy = NetlinkProxy(policy='forward', nl=self)
        self._rproxy.pmap = {rtnl.RTM_NEWLINK: proxy_linkinfo}

    def bind(self, groups=rtnl.RTNL_GROUPS, async=False):
        super(IPRSocketMixin, self).bind(groups, async=async)

    ##
    # proxy-ng protocol
    #
    def sendto(self, data, address):
        ret = self._sproxy.handle(data)
        if ret is not None:
            if ret['verdict'] == 'forward':
                return self._sendto(ret['data'], address)
            elif ret['verdict'] in ('return', 'error'):
                if self._s_channel is not None:
                    return self._s_channel.send(ret['data'])
                else:
                    msgs = self.marshal.parse(ret['data'])
                    for msg in msgs:
                        seq = msg['header']['sequence_number']
                        if seq in self.backlog:
                            self.backlog[seq].append(msg)
                        else:
                            self.backlog[seq] = [msg]
                    return len(ret['data'])
            else:
                ValueError('Incorrect verdict')

        return self._sendto(data, address)

    def recv(self, bufsize, flags=0):
        data = self._recv(bufsize, flags)
        ret = self._rproxy.handle(data)
        if ret is not None:
            if ret['verdict'] in ('forward', 'error'):
                return ret['data']
            else:
                ValueError('Incorrect verdict')

        return data


class IPRSocket(IPRSocketMixin, NetlinkSocket):
    '''
    The simplest class, that connects together the netlink parser and
    a generic Python socket implementation. Provides method get() to
    receive the next message from netlink socket and parse it. It is
    just simple socket-like class, it implements no buffering or
    like that. It spawns no additional threads, leaving this up to
    developers.

    Please note, that netlink is an asynchronous protocol with
    non-guaranteed delivery. You should be fast enough to get all the
    messages in time. If the message flow rate is higher than the
    speed you parse them with, exceeding messages will be dropped.

    *Usage*

    Threadless RT netlink monitoring with blocking I/O calls:

        >>> from pyroute2 import IPRSocket
        >>> from pprint import pprint
        >>> s = IPRSocket()
        >>> s.bind()
        >>> pprint(s.get())
        [{'attrs': [('RTA_TABLE', 254),
                    ('RTA_DST', '2a00:1450:4009:808::1002'),
                    ('RTA_GATEWAY', 'fe80:52:0:2282::1fe'),
                    ('RTA_OIF', 2),
                    ('RTA_PRIORITY', 0),
                    ('RTA_CACHEINFO', {'rta_clntref': 0,
                                       'rta_error': 0,
                                       'rta_expires': 0,
                                       'rta_id': 0,
                                       'rta_lastuse': 5926,
                                       'rta_ts': 0,
                                       'rta_tsage': 0,
                                       'rta_used': 1})],
          'dst_len': 128,
          'event': 'RTM_DELROUTE',
          'family': 10,
          'flags': 512,
          'header': {'error': None,
                     'flags': 0,
                     'length': 128,
                     'pid': 0,
                     'sequence_number': 0,
                     'type': 25},
          'proto': 9,
          'scope': 0,
          'src_len': 0,
          'table': 254,
          'tos': 0,
          'type': 1}]
        >>>
    '''
    pass
