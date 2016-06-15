# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.
# -*- coding: utf-8 -*-
'''
IPRoute module
==============

iproute quickstart
------------------

**IPRoute** in two words::

    $ sudo pip install pyroute2

    $ cat example.py
    from pyroute2 import IPRoute
    ip = IPRoute()
    print([x.get_attr('IFLA_IFNAME') for x in ip.get_links()])

    $ python example.py
    ['lo', 'p6p1', 'wlan0', 'virbr0', 'virbr0-nic']

threaded vs. threadless architecture
------------------------------------

Since v0.3.2, IPRoute class is threadless by default.
It spawns no additional threads, and receives only
responses to own requests, no broadcast messages. So,
if you prefer not to cope with implicit threading, you
can safely use this module.

To get broadcast messages, use `IPRoute.bind()` call.
Please notice, that after calling `IPRoute.bind()` you
MUST get all the messages in time. In the case of the
kernel buffer overflow, you will have to restart the
socket.

With `IPRoute.bind(async=True)` one can launch async
message receiver thread with `Queue`-based buffer. The
buffer is thread-safe and completely transparent from
the programmer's perspective. Please read also
`NetlinkSocket` documentation to know more about async
mode.

think about IPDB
----------------

If you plan to regularly fetch loads of objects, think
about IPDB also. Unlike to IPRoute, IPDB does not fetch
all the objects from OS every time you request them, but
keeps a cache that is asynchronously updated by the netlink
broadcasts. For a long-term running programs, that often
retrieve info about hundreds or thousands of objects, it
can be better to use IPDB as it will load CPU significantly
less.

classes
-------
'''

from socket import htons
from socket import AF_INET
from socket import AF_INET6
from socket import AF_UNSPEC
from pyroute2.netlink import NLMSG_ERROR
from pyroute2.netlink import NLM_F_ATOMIC
from pyroute2.netlink import NLM_F_ROOT
from pyroute2.netlink import NLM_F_REPLACE
from pyroute2.netlink import NLM_F_REQUEST
from pyroute2.netlink import NLM_F_ACK
from pyroute2.netlink import NLM_F_DUMP
from pyroute2.netlink import NLM_F_CREATE
from pyroute2.netlink import NLM_F_EXCL
from pyroute2.netlink.rtnl import RTM_NEWADDR
from pyroute2.netlink.rtnl import RTM_GETADDR
from pyroute2.netlink.rtnl import RTM_DELADDR
from pyroute2.netlink.rtnl import RTM_NEWLINK
from pyroute2.netlink.rtnl import RTM_GETLINK
from pyroute2.netlink.rtnl import RTM_DELLINK
from pyroute2.netlink.rtnl import RTM_NEWQDISC
from pyroute2.netlink.rtnl import RTM_GETQDISC
from pyroute2.netlink.rtnl import RTM_DELQDISC
from pyroute2.netlink.rtnl import RTM_NEWTFILTER
from pyroute2.netlink.rtnl import RTM_GETTFILTER
from pyroute2.netlink.rtnl import RTM_DELTFILTER
from pyroute2.netlink.rtnl import RTM_NEWTCLASS
from pyroute2.netlink.rtnl import RTM_GETTCLASS
from pyroute2.netlink.rtnl import RTM_DELTCLASS
from pyroute2.netlink.rtnl import RTM_GETNEIGH
from pyroute2.netlink.rtnl import RTM_NEWRULE
from pyroute2.netlink.rtnl import RTM_GETRULE
from pyroute2.netlink.rtnl import RTM_DELRULE
from pyroute2.netlink.rtnl import RTM_NEWROUTE
from pyroute2.netlink.rtnl import RTM_GETROUTE
from pyroute2.netlink.rtnl import RTM_DELROUTE
from pyroute2.netlink.rtnl import RTM_SETLINK
from pyroute2.netlink.rtnl import TC_H_INGRESS
from pyroute2.netlink.rtnl import TC_H_ROOT
from pyroute2.netlink.rtnl import rtprotos
from pyroute2.netlink.rtnl import rtypes
from pyroute2.netlink.rtnl import rtscopes
from pyroute2.netlink.rtnl.req import IPLinkRequest
from pyroute2.netlink.rtnl.tcmsg import get_htb_parameters
from pyroute2.netlink.rtnl.tcmsg import get_htb_class_parameters
from pyroute2.netlink.rtnl.tcmsg import get_tbf_parameters
from pyroute2.netlink.rtnl.tcmsg import get_sfq_parameters
from pyroute2.netlink.rtnl.tcmsg import get_u32_parameters
from pyroute2.netlink.rtnl.tcmsg import get_netem_parameters
from pyroute2.netlink.rtnl.tcmsg import get_fw_parameters
from pyroute2.netlink.rtnl.tcmsg import tcmsg
from pyroute2.netlink.rtnl.rtmsg import rtmsg
from pyroute2.netlink.rtnl.ndmsg import ndmsg
from pyroute2.netlink.rtnl.fibmsg import fibmsg
from pyroute2.netlink.rtnl.fibmsg import FR_ACT_NAMES
from pyroute2.netlink.rtnl.ifinfmsg import ifinfmsg
from pyroute2.netlink.rtnl.ifaddrmsg import ifaddrmsg
from pyroute2.netlink.rtnl.iprsocket import IPRSocket

from pyroute2.common import basestring

DEFAULT_TABLE = 254


def transform_handle(handle):
    if isinstance(handle, basestring):
        (major, minor) = [int(x if x else '0', 16) for x in handle.split(':')]
        handle = (major << 8 * 2) | minor
    return handle


class IPRouteMixin(object):
    '''
    `IPRouteMixin` should not be instantiated by itself. It is intended
    to be used as a mixin class that provides iproute2-like API. You
    should use `IPRoute` or `NetNS` classes.

    All following info you can consider as IPRoute info as well.

    It is an old-school API, that provides access to rtnetlink as is.
    It helps you to retrieve and change almost all the data, available
    through rtnetlink::

        from pyroute2 import IPRoute
        ipr = IPRoute()
            # lookup interface by name
        dev = ipr.link_lookup(ifname='tap0')[0]
            # bring it down
        ipr.link('set', dev, state='down')
            # change interface MAC address and rename it
        ipr.link('set', dev, address='00:11:22:33:44:55', ifname='vpn')
            # add primary IP address
        ipr.addr('add', dev, address='10.0.0.1', mask=24)
            # add secondary IP address
        ipr.addr('add', dev, address='10.0.0.2', mask=24)
            # bring it up
        ipr.link('set', dev, state='up')

    '''

    # 8<---------------------------------------------------------------
    #
    # Listing methods
    #
    def get_qdiscs(self, index=None):
        '''
        Get all queue disciplines for all interfaces or for specified
        one.
        '''
        msg = tcmsg()
        msg['family'] = AF_UNSPEC
        ret = self.nlm_request(msg, RTM_GETQDISC)
        if index is None:
            return ret
        else:
            return [x for x in ret if x['index'] == index]

    def get_filters(self, index=0, handle=0, parent=0):
        '''
        Get filters for specified interface, handle and parent.
        '''
        msg = tcmsg()
        msg['family'] = AF_UNSPEC
        msg['index'] = index
        msg['handle'] = handle
        msg['parent'] = parent
        return self.nlm_request(msg, RTM_GETTFILTER)

    def get_classes(self, index=0):
        '''
        Get classes for specified interface.
        '''
        msg = tcmsg()
        msg['family'] = AF_UNSPEC
        msg['index'] = index
        return self.nlm_request(msg, RTM_GETTCLASS)

    def get_links(self, *argv, **kwarg):
        '''
        Get network interfaces.

        By default returns all interfaces. Arguments vector
        can contain interface indices or a special keyword
        'all'::

            ip.get_links()
            ip.get_links('all')
            ip.get_links(1, 2, 3)

            interfaces = [1, 2, 3]
            ip.get_links(*interfaces)
        '''
        result = []
        links = argv or ['all']
        msg_flags = NLM_F_REQUEST | NLM_F_DUMP
        for index in links:
            msg = ifinfmsg()
            msg['family'] = kwarg.get('family', AF_UNSPEC)
            if index != 'all':
                msg['index'] = index
                msg_flags = NLM_F_REQUEST
            result.extend(self.nlm_request(msg, RTM_GETLINK, msg_flags))
        return result

    def get_neighbors(self, family=AF_UNSPEC):
        '''
        Retrieve ARP cache records.
        '''
        msg = ndmsg()
        msg['family'] = family
        return self.nlm_request(msg, RTM_GETNEIGH)

    def get_addr(self, family=AF_UNSPEC, index=None):
        '''
        Get addresses::
            ip.get_addr()  # get all addresses
            ip.get_addr(index=2)  # get addresses for the 2nd interface
        '''
        msg = ifaddrmsg()
        msg['family'] = family
        ret = self.nlm_request(msg, RTM_GETADDR)
        if index is not None:
            return [x for x in ret if x.get('index') == index]
        else:
            return ret

    def get_rules(self, family=AF_UNSPEC):
        '''
        Get all rules.
        You can specify inet family, by default return rules for all families.

        Example::
            ip.get_rules() # get all the rules for all families
            ip.get_routes(family=AF_INET6)  # get only IPv6 rules
        '''
        msg = fibmsg()
        msg['family'] = family
        msg_flags = NLM_F_REQUEST | NLM_F_ROOT | NLM_F_ATOMIC
        return self.nlm_request(msg, RTM_GETRULE, msg_flags)

    def get_routes(self, family=AF_INET, **kwarg):
        '''
        Get all routes. You can specify the table. There
        are 255 routing classes (tables), and the kernel
        returns all the routes on each request. So the
        routine filters routes from full output.

        Example::

            ip.get_routes()  # get all the routes for all families
            ip.get_routes(family=AF_INET6)  # get only IPv6 routes
            ip.get_routes(table=254)  # get routes from 254 table
        '''

        msg_flags = NLM_F_DUMP | NLM_F_REQUEST
        msg = rtmsg()
        # you can specify the table here, but the kernel
        # will ignore this setting
        table = kwarg.get('table', DEFAULT_TABLE)
        msg['table'] = table if table <= 255 else 252

        # explicitly look for IPv6
        if any([kwarg.get(x, '').find(':') >= 0 for x
                in ('dst', 'src', 'gateway', 'prefsrc')]):
            family = AF_INET6
        msg['family'] = family

        # get a particular route
        if kwarg.get('dst', None) is not None:
            dlen = 32 if family == AF_INET else \
                128 if family == AF_INET6 else 0
            msg_flags = NLM_F_REQUEST
            msg['dst_len'] = kwarg.get('dst_len', dlen)

        for key in kwarg:
            nla = rtmsg.name2nla(key)
            if kwarg[key] is not None:
                msg['attrs'].append([nla, kwarg[key]])

        routes = self.nlm_request(msg, RTM_GETROUTE, msg_flags)
        return [x for x in routes
                if x.get_attr('RTA_TABLE') == table or
                kwarg.get('table', None) is None]
    # 8<---------------------------------------------------------------

    # 8<---------------------------------------------------------------
    #
    # Shortcuts
    #
    # addr_add(), addr_del(), route_add(), route_del() shortcuts are
    # removed due to redundancy. Only link shortcuts are left here for
    # now. Possibly, they should be moved to a separate module.
    #
    def get_default_routes(self, family=AF_UNSPEC, table=DEFAULT_TABLE):
        '''
        Get default routes
        '''
        # according to iproute2/ip/iproute.c:print_route()
        return [x for x in self.get_routes(family, table=table)
                if (x.get_attr('RTA_DST', None) is None and
                    x['dst_len'] == 0)]

    def link_create(self, **kwarg):
        '''
        Create a link. The method parameters will be
        passed to the `IPLinkRequest()` constructor as
        a dictionary.

        Examples::

            ip.link_create(ifname='very_dummy', kind='dummy')
            ip.link_create(ifname='br0', kind='bridge')
            ip.link_create(ifname='v101', kind='vlan', vlan_id=101, link=1)
        '''
        return self.link('add', **IPLinkRequest(kwarg))

    def link_up(self, index):
        '''
        Switch an interface up unconditionally.
        '''
        self.link('set', index=index, state='up')

    def link_down(self, index):
        '''
        Switch an interface down unconditilnally.
        '''
        self.link('set', index=index, state='down')

    def link_rename(self, index, name):
        '''
        Rename an interface. Please note, that the interface must be
        in the `DOWN` state in order to be renamed, otherwise you
        will get an error.
        '''
        self.link('set', index=index, ifname=name)

    def link_remove(self, index):
        '''
        Remove an interface
        '''
        self.link('delete', index=index)

    def link_lookup(self, **kwarg):
        '''
        Lookup interface index (indeces) by first level NLA
        value.

        Example::

            ip.link_lookup(address="52:54:00:9d:4e:3d")
            ip.link_lookup(ifname="lo")
            ip.link_lookup(operstate="UP")

        Please note, that link_lookup() returns list, not one
        value.
        '''
        name = tuple(kwarg.keys())[0]
        value = kwarg[name]

        name = str(name).upper()
        if not name.startswith('IFLA_'):
            name = 'IFLA_%s' % (name)

        return [k['index'] for k in
                [i for i in self.get_links() if 'attrs' in i] if
                [l for l in k['attrs'] if l[0] == name and l[1] == value]]

    def flush_routes(self, *argv, **kwarg):
        '''
        Flush routes -- purge route records from a table.
        Arguments are the same as for `get_routes()`
        routine. Actually, this routine implements a pipe from
        `get_routes()` to `nlm_request()`.
        '''
        flags = NLM_F_ACK | NLM_F_CREATE | NLM_F_EXCL | NLM_F_REQUEST
        ret = []
        kwarg['table'] = kwarg.get('table', DEFAULT_TABLE)
        for route in self.get_routes(*argv, **kwarg):
            ret.append(self.nlm_request(route,
                                        msg_type=RTM_DELROUTE,
                                        msg_flags=flags))
        return ret
    # 8<---------------------------------------------------------------

    # 8<---------------------------------------------------------------
    #
    # General low-level configuration methods
    #
    def link(self, command, **kwarg):
        '''
        Link operations.

        * command -- set, add or delete
        * index -- device index
        * \*\*kwarg -- keywords, NLA

        Example::

            x = 62  # interface index
            ip.link("set", index=x, state="down")
            ip.link("set", index=x, address="00:11:22:33:44:55", name="bala")
            ip.link("set", index=x, mtu=1000, txqlen=2000)
            ip.link("set", index=x, state="up")

        Keywords "state", "flags" and "mask" are reserved. State can
        be "up" or "down", it is a shortcut::

            state="up":   flags=1, mask=1
            state="down": flags=0, mask=0

        For more flags grep IFF in the kernel code, until we write
        human-readable flag resolver.

        Other keywords are from ifinfmsg.nla_map, look into the
        corresponding module. You can use the form "ifname" as well
        as "IFLA_IFNAME" and so on, so that's equal::

            ip.link("set", index=x, mtu=1000)
            ip.link("set", index=x, IFLA_MTU=1000)

        You can also delete interface with::

            ip.link("delete", index=x)
        '''

        commands = {'set': RTM_SETLINK,
                    'add': RTM_NEWLINK,
                    'del': RTM_DELLINK,
                    'remove': RTM_DELLINK,
                    'delete': RTM_DELLINK}
        command = commands.get(command, command)

        msg_flags = NLM_F_REQUEST | NLM_F_ACK | NLM_F_CREATE | NLM_F_EXCL
        msg = ifinfmsg()
        # index is required
        msg['index'] = kwarg.get('index')

        flags = kwarg.pop('flags', 0) or 0
        mask = kwarg.pop('mask', 0) or kwarg.pop('change', 0) or 0

        if 'state' in kwarg:
            mask = 1                  # IFF_UP mask
            if kwarg['state'].lower() == 'up':
                flags = 1             # 0 (down) or 1 (up)
            del kwarg['state']

        msg['flags'] = flags
        msg['change'] = mask

        for key in kwarg:
            nla = type(msg).name2nla(key)
            if kwarg[key] is not None:
                msg['attrs'].append([nla, kwarg[key]])

        return self.nlm_request(msg, msg_type=command, msg_flags=msg_flags)

    def addr(self, command, index, address, mask=24,
             family=None, scope=0, **kwarg):
        '''
        Address operations

        * command -- add, delete
        * index -- device index
        * address -- IPv4 or IPv6 address
        * mask -- address mask
        * family -- socket.AF_INET for IPv4 or socket.AF_INET6 for IPv6
        * scope -- the address scope, see /etc/iproute2/rt_scopes

        Example::

            index = 62
            ip.addr("add", index, address="10.0.0.1", mask=24)
            ip.addr("add", index, address="10.0.0.2", mask=24)
        '''

        commands = {'add': RTM_NEWADDR,
                    'del': RTM_DELADDR,
                    'remove': RTM_DELADDR,
                    'delete': RTM_DELADDR}
        command = commands.get(command, command)

        flags = NLM_F_REQUEST | NLM_F_ACK | NLM_F_CREATE | NLM_F_EXCL

        # try to guess family, if it is not forced
        if family is None:
            if address.find(":") > -1:
                family = AF_INET6
            else:
                family = AF_INET

        msg = ifaddrmsg()
        msg['index'] = index
        msg['family'] = family
        msg['prefixlen'] = mask
        msg['scope'] = scope
        if family == AF_INET:
            msg['attrs'] = [['IFA_LOCAL', address],
                            ['IFA_ADDRESS', address]]
        elif family == AF_INET6:
            msg['attrs'] = [['IFA_ADDRESS', address]]
        for key in kwarg:
            nla = ifaddrmsg.name2nla(key)
            if kwarg[key] is not None:
                msg['attrs'].append([nla, kwarg[key]])
        return self.nlm_request(msg,
                                msg_type=command,
                                msg_flags=flags,
                                terminate=lambda x: x['header']['type'] ==
                                NLMSG_ERROR)

    def tc(self, command, kind, index, handle=0, **kwarg):
        '''
        "Swiss knife" for traffic control. With the method you can
        add, delete or modify qdiscs, classes and filters.

        * command -- add or delete qdisc, class, filter.
        * kind -- a string identifier -- "sfq", "htb", "u32" and so on.
        * handle -- integer or string

        Command can be one of ("add", "del", "add-class", "del-class",
        "add-filter", "del-filter") (see `commands` dict in the code).

        Handle notice: traditional iproute2 notation, like "1:0", actually
        represents two parts in one four-bytes integer::

            1:0    ->    0x10000
            1:1    ->    0x10001
            ff:0   ->   0xff0000
            ffff:1 -> 0xffff0001

        For pyroute2 tc() you can use both forms: integer like 0xffff0000
        or string like 'ffff:0000'. By default, handle is 0, so you can add
        simple classless queues w/o need to specify handle. Ingress queue
        causes handle to be 0xffff0000.

        So, to set up sfq queue on interface 1, the function call
        will be like that::

            ip = IPRoute()
            ip.tc("add", "sfq", 1)

        Instead of string commands ("add", "del"...), you can use also
        module constants, `RTM_NEWQDISC`, `RTM_DELQDISC` and so on::

            ip = IPRoute()
            ip.tc(RTM_NEWQDISC, "sfq", 1)

        More complex example with htb qdisc, lets assume eth0 == 2::

            #          u32 -->    +--> htb 1:10 --> sfq 10:0
            #          |          |
            #          |          |
            # eth0 -- htb 1:0 -- htb 1:1
            #          |          |
            #          |          |
            #          u32 -->    +--> htb 1:20 --> sfq 20:0

            eth0 = 2
            # add root queue 1:0
            ip.tc("add", "htb", eth0, 0x10000, default=0x200000)

            # root class 1:1
            ip.tc("add-class", "htb", eth0, 0x10001,
                  parent=0x10000,
                  rate="256kbit",
                  burst=1024 * 6)

            # two branches: 1:10 and 1:20
            ip.tc("add-class", "htb", eth0, 0x10010,
                  parent=0x10001,
                  rate="192kbit",
                  burst=1024 * 6,
                  prio=1)
            ip.tc("add-class", "htb", eht0, 0x10020,
                  parent=0x10001,
                  rate="128kbit",
                  burst=1024 * 6,
                  prio=2)

            # two leaves: 10:0 and 20:0
            ip.tc("add", "sfq", eth0, 0x100000,
                  parent=0x10010,
                  perturb=10)
            ip.tc("add", "sfq", eth0, 0x200000,
                  parent=0x10020,
                  perturb=10)

            # two filters: one to load packets into 1:10 and the
            # second to 1:20
            ip.tc("add-filter", "u32", eth0,
                  parent=0x10000,
                  prio=10,
                  protocol=socket.AF_INET,
                  target=0x10010,
                  keys=["0x0006/0x00ff+8", "0x0000/0xffc0+2"])
            ip.tc("add-filter", "u32", eth0,
                  parent=0x10000,
                  prio=10,
                  protocol=socket.AF_INET,
                  target=0x10020,
                  keys=["0x5/0xf+0", "0x10/0xff+33"])
        '''

        commands = {'add': RTM_NEWQDISC,
                    'del': RTM_DELQDISC,
                    'remove': RTM_DELQDISC,
                    'delete': RTM_DELQDISC,
                    'add-class': RTM_NEWTCLASS,
                    'del-class': RTM_DELTCLASS,
                    'add-filter': RTM_NEWTFILTER,
                    'del-filter': RTM_DELTFILTER}
        command = commands.get(command, command)
        flags = NLM_F_REQUEST | NLM_F_ACK | NLM_F_CREATE | NLM_F_EXCL
        msg = tcmsg()
        # transform handle, parent and target, if needed:
        handle = transform_handle(handle)
        for item in ('parent', 'target', 'default'):
            if item in kwarg and kwarg[item] is not None:
                kwarg[item] = transform_handle(kwarg[item])
        msg['index'] = index
        msg['handle'] = handle
        opts = kwarg.get('opts', None)
        if kind == 'ingress':
            msg['parent'] = TC_H_INGRESS
            msg['handle'] = 0xffff0000
        elif kind == 'tbf':
            msg['parent'] = TC_H_ROOT
            if kwarg:
                opts = get_tbf_parameters(kwarg)
        elif kind == 'htb':
            msg['parent'] = kwarg.get('parent', TC_H_ROOT)
            if kwarg:
                if command in (RTM_NEWQDISC, RTM_DELQDISC):
                    opts = get_htb_parameters(kwarg)
                elif command in (RTM_NEWTCLASS, RTM_DELTCLASS):
                    opts = get_htb_class_parameters(kwarg)
        elif kind == 'netem':
            msg['parent'] = kwarg.get('parent', TC_H_ROOT)
            if kwarg:
                opts = get_netem_parameters(kwarg)
        elif kind == 'sfq':
            msg['parent'] = kwarg.get('parent', TC_H_ROOT)
            if kwarg:
                opts = get_sfq_parameters(kwarg)
        elif kind == 'u32':
            msg['parent'] = kwarg.get('parent')
            msg['info'] = htons(kwarg.get('protocol', 0) & 0xffff) |\
                ((kwarg.get('prio', 0) << 16) & 0xffff0000)
            if kwarg:
                opts = get_u32_parameters(kwarg)
        elif kind == 'fw':
            msg['parent'] = kwarg.get('parent')
            msg['info'] = htons(kwarg.get('protocol', 0) & 0xffff) |\
                ((kwarg.get('prio', 0) << 16) & 0xffff0000)
            if kwarg:
                opts = get_fw_parameters(kwarg)
        else:
            msg['parent'] = kwarg.get('parent', TC_H_ROOT)

        if kind is not None:
            msg['attrs'] = [['TCA_KIND', kind]]
        if opts is not None:
            msg['attrs'].append(['TCA_OPTIONS', opts])
        return self.nlm_request(msg, msg_type=command, msg_flags=flags)

    def route(self, command,
              rtype='RTN_UNICAST',
              rtproto='RTPROT_STATIC',
              rtscope='RT_SCOPE_UNIVERSE',
              **kwarg):
        '''
        Route operations

        * command -- add, delete, change, replace
        * prefix -- route prefix
        * mask -- route prefix mask
        * rtype -- route type (default: "RTN_UNICAST")
        * rtproto -- routing protocol (default: "RTPROT_STATIC")
        * rtscope -- routing scope (default: "RT_SCOPE_UNIVERSE")
        * family -- socket.AF_INET (default) or socket.AF_INET6

        `pyroute2/netlink/rtnl/rtmsg.py` rtmsg.nla_map:

        * table -- routing table to use (default: 254)
        * gateway -- via address
        * prefsrc -- preferred source IP address
        * dst -- the same as `prefix`
        * src -- source address
        * iif -- incoming traffic interface
        * oif -- outgoing traffic interface

        etc.

        Example::

            ip.route("add", dst="10.0.0.0", mask=24, gateway="192.168.0.1")

        Commands `change` and `replace` have the same meanings, as
        in ip-route(8): `change` modifies only existing route, while
        `replace` creates a new one, if there is no such route yet.
        '''

        # 8<----------------------------------------------------
        # FIXME
        # flags should be moved to some more general place
        flags_base = NLM_F_REQUEST | NLM_F_ACK
        flags_make = flags_base | NLM_F_CREATE | NLM_F_EXCL
        flags_change = flags_base | NLM_F_REPLACE
        flags_replace = flags_change | NLM_F_CREATE
        # 8<----------------------------------------------------
        commands = {'add': (RTM_NEWROUTE, flags_make),
                    'set': (RTM_NEWROUTE, flags_replace),
                    'replace': (RTM_NEWROUTE, flags_replace),
                    'change': (RTM_NEWROUTE, flags_change),
                    'del': (RTM_DELROUTE, flags_make),
                    'remove': (RTM_DELROUTE, flags_make),
                    'delete': (RTM_DELROUTE, flags_make)}
        (command, flags) = commands.get(command, command)
        msg = rtmsg()
        # table is mandatory; by default == 254
        # if table is not defined in kwarg, save it there
        # also for nla_attr:
        table = kwarg.get('table', 254)
        msg['table'] = table if table <= 255 else 252
        msg['family'] = kwarg.get('family', AF_INET)
        msg['proto'] = rtprotos[rtproto]
        msg['type'] = rtypes[rtype]
        msg['scope'] = rtscopes[rtscope]
        msg['dst_len'] = kwarg.get('dst_len', None) or \
            kwarg.get('mask', 0)
        msg['attrs'] = []
        # FIXME
        # deprecated "prefix" support:
        if 'prefix' in kwarg:
            kwarg['dst'] = kwarg['prefix']

        for key in kwarg:
            nla = rtmsg.name2nla(key)
            if kwarg[key] is not None:
                msg['attrs'].append([nla, kwarg[key]])

        return self.nlm_request(msg, msg_type=command,
                                msg_flags=flags)

    def rule(self, command, table, priority=32000,
             action='FR_ACT_NOP', family=AF_INET,
             src=None, src_len=None,
             dst=None, dst_len=None,
             fwmark=None, iifname=None, oifname=None):
        '''
        Rule operations

            - command — add, delete
            - table — 0 < table id < 253
            - priority — 0 < rule's priority < 32766
            - action — type of rule, default 'FR_ACT_NOP' (see fibmsg.py)
            - rtscope — routing scope, default RT_SCOPE_UNIVERSE
                `(RT_SCOPE_UNIVERSE|RT_SCOPE_SITE|\
                RT_SCOPE_LINK|RT_SCOPE_HOST|RT_SCOPE_NOWHERE)`
            - family — rule's family (socket.AF_INET (default) or
                socket.AF_INET6)
            - src — IP source for Source Based (Policy Based) routing's rule
            - dst — IP for Destination Based (Policy Based) routing's rule
            - src_len — Mask for Source Based (Policy Based) routing's rule
            - dst_len — Mask for Destination Based (Policy Based) routing's
                rule
            - iifname — Input interface for Interface Based (Policy Based)
                routing's rule
            - oifname — Output interface for Interface Based (Policy Based)
                routing's rule

        Example::
            ip.rule('add', 10, 32000)

        Will create::
            #ip ru sh
            ...
            32000: from all lookup 10
            ....

        Example::
            iproute.rule('add', 11, 32001, 'FR_ACT_UNREACHABLE')

        Will create::
            #ip ru sh
            ...
            32001: from all lookup 11 unreachable
            ....

        Example::
            iproute.rule('add', 14, 32004, src='10.64.75.141')

        Will create::
            #ip ru sh
            ...
            32004: from 10.64.75.141 lookup 14
            ...

        Example::
            iproute.rule('add', 15, 32005, dst='10.64.75.141', dst_len=24)

        Will create::
            #ip ru sh
            ...
            32005: from 10.64.75.141/24 lookup 15
            ...

        Example::
            iproute.rule('add', 15, 32006, dst='10.64.75.141', fwmark=10)

        Will create::
            #ip ru sh
            ...
            32006: from 10.64.75.141 fwmark 0xa lookup 15
            ...
        '''
        if table < 0:
            raise ValueError('unsupported table number')

        commands = {'add': RTM_NEWRULE,
                    'del': RTM_DELRULE,
                    'remove': RTM_DELRULE,
                    'delete': RTM_DELRULE}
        command = commands.get(command, command)

        msg_flags = NLM_F_REQUEST | NLM_F_ACK | NLM_F_CREATE | NLM_F_EXCL
        msg = fibmsg()
        msg['table'] = table if table <= 255 else 252
        msg['family'] = family
        msg['action'] = FR_ACT_NAMES[action]
        msg['attrs'] = [['FRA_TABLE', table]]
        msg['attrs'].append(['FRA_PRIORITY', priority])
        if fwmark is not None:
            msg['attrs'].append(['FRA_FWMARK', fwmark])
        addr_len = {AF_INET6: 128, AF_INET:  32}[family]
        if(dst_len is not None and dst_len >= 0 and dst_len <= addr_len):
            msg['dst_len'] = dst_len
        else:
            msg['dst_len'] = 0
        if(src_len is not None and src_len >= 0 and src_len <= addr_len):
            msg['src_len'] = src_len
        else:
            msg['src_len'] = 0
        if src is not None:
            msg['attrs'].append(['FRA_SRC', src])
            if src_len is None:
                msg['src_len'] = addr_len
        if dst is not None:
            msg['attrs'].append(['FRA_DST', dst])
            if dst_len is None:
                msg['dst_len'] = addr_len
        if iifname is not None:
            msg['attrs'].append(['FRA_IIFNAME', iifname])
        if oifname is not None:
            msg['attrs'].append(['FRA_OIFNAME', oifname])

        return self.nlm_request(msg, msg_type=command,
                                msg_flags=msg_flags)
    # 8<---------------------------------------------------------------


class IPRoute(IPRouteMixin, IPRSocket):
    '''
    Production class that provides iproute API over normal Netlink
    socket.

    You can think of this class in some way as of plain old iproute2
    utility.
    '''
    pass
