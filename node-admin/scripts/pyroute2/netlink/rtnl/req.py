# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.
from socket import AF_INET6
from pyroute2.common import basestring
from pyroute2.netlink.rtnl.ifinfmsg import ifinfmsg
from pyroute2.netlink.rtnl.rtmsg import rtmsg


class IPRequest(dict):

    def __init__(self, obj=None):
        dict.__init__(self)
        if obj is not None:
            self.update(obj)

    def update(self, obj):
        for key in obj:
            if obj[key] is not None:
                self[key] = obj[key]


class IPRouteRequest(IPRequest):
    '''
    Utility class, that converts human-readable dictionary
    into RTNL route request.
    '''

    def __setitem__(self, key, value):
        # fix family
        if isinstance(value, basestring) and value.find(':') >= 0:
            self['family'] = AF_INET6
        # work on the rest
        if key == 'dst':
            if value != 'default':
                value = value.split('/')
                if len(value) == 1:
                    dst = value[0]
                    mask = 0
                elif len(value) == 2:
                    dst = value[0]
                    mask = int(value[1])
                else:
                    raise ValueError('wrong destination')
                dict.__setitem__(self, 'dst', dst)
                dict.__setitem__(self, 'dst_len', mask)
        elif key == 'metrics':
            ret = {'attrs': []}
            for name in value:
                rtax = rtmsg.metrics.name2nla(name)
                ret['attrs'].append([rtax, value[name]])
            dict.__setitem__(self, 'metrics', ret)
        else:
            dict.__setitem__(self, key, value)


class CBRequest(IPRequest):
    '''
    FIXME
    '''
    commands = None
    msg = None

    def __init__(self, *argv, **kwarg):
        self['commands'] = {'attrs': []}

    def __setitem__(self, key, value):
        if value is None:
            return
        if key in self.commands:
            self['commands']['attrs'].\
                append([self.msg.name2nla(key), value])
        else:
            dict.__setitem__(self, key, value)


class IPLinkRequest(IPRequest):
    '''
    Utility class, that converts human-readable dictionary
    into RTNL link request.
    '''
    blacklist = ['carrier',
                 'carrier_changes']

    def __init__(self, *argv, **kwarg):
        self.deferred = []
        IPRequest.__init__(self, *argv, **kwarg)
        if 'index' not in self:
            self['index'] = 0

    def __setitem__(self, key, value):
        # ignore blacklisted attributes
        if key in self.blacklist:
            return

        # there must be no "None" values in the request
        if value is None:
            return

        # all the values must be in ascii
        try:
            if isinstance(value, unicode):
                value = value.encode('ascii')
        except NameError:
            pass

        # set up specific keys
        if key == 'kind':
            self['IFLA_LINKINFO'] = {'attrs': []}
            linkinfo = self['IFLA_LINKINFO']['attrs']
            linkinfo.append(['IFLA_INFO_KIND', value])
            if value in ('vlan', 'bond', 'tuntap', 'veth',
                         'vxlan', 'macvlan', 'macvtap', 'gre'):
                linkinfo.append(['IFLA_INFO_DATA', {'attrs': []}])
        elif key == 'vlan_id':
            nla = ['IFLA_VLAN_ID', value]
            # FIXME: we need to replace, not add
            self.defer_nla(nla, ('IFLA_LINKINFO', 'IFLA_INFO_DATA'),
                           lambda x: x.get('kind', None) == 'vlan')
        elif key == 'gid':
            nla = ['IFTUN_UID', value]
            self.defer_nla(nla, ('IFLA_LINKINFO', 'IFLA_INFO_DATA'),
                           lambda x: x.get('kind', None) == 'tuntap')
        elif key == 'uid':
            nla = ['IFTUN_UID', value]
            self.defer_nla(nla, ('IFLA_LINKINFO', 'IFLA_INFO_DATA'),
                           lambda x: x.get('kind', None) == 'tuntap')
        elif key == 'mode':
            nla = ['IFTUN_MODE', value]
            self.defer_nla(nla, ('IFLA_LINKINFO', 'IFLA_INFO_DATA'),
                           lambda x: x.get('kind', None) == 'tuntap')
            nla = ['IFLA_BOND_MODE', value]
            self.defer_nla(nla, ('IFLA_LINKINFO', 'IFLA_INFO_DATA'),
                           lambda x: x.get('kind', None) == 'bond')
        elif key == 'ifr':
            nla = ['IFTUN_IFR', value]
            self.defer_nla(nla, ('IFLA_LINKINFO', 'IFLA_INFO_DATA'),
                           lambda x: x.get('kind', None) == 'tuntap')
        elif key.startswith('macvtap'):
            nla = [ifinfmsg.name2nla(key), value]
            self.defer_nla(nla, ('IFLA_LINKINFO', 'IFLA_INFO_DATA'),
                           lambda x: x.get('kind', None) == 'macvtap')
        elif key.startswith('macvlan'):
            nla = [ifinfmsg.name2nla(key), value]
            self.defer_nla(nla, ('IFLA_LINKINFO', 'IFLA_INFO_DATA'),
                           lambda x: x.get('kind', None) == 'macvlan')
        elif key.startswith('gre'):
            nla = [ifinfmsg.name2nla(key), value]
            self.defer_nla(nla, ('IFLA_LINKINFO', 'IFLA_INFO_DATA'),
                           lambda x: x.get('kind', None) == 'gre')
        elif key.startswith('vxlan'):
            nla = [ifinfmsg.name2nla(key), value]
            self.defer_nla(nla, ('IFLA_LINKINFO', 'IFLA_INFO_DATA'),
                           lambda x: x.get('kind', None) == 'vxlan')
        elif key == 'peer':
            nla = ['VETH_INFO_PEER', {'attrs': [['IFLA_IFNAME', value]]}]
            self.defer_nla(nla, ('IFLA_LINKINFO', 'IFLA_INFO_DATA'),
                           lambda x: x.get('kind', None) == 'veth')
        dict.__setitem__(self, key, value)
        if self.deferred:
            self.flush_deferred()

    def flush_deferred(self):
        deferred = []
        for nla, path, predicate in self.deferred:
            if predicate(self):
                self.append_nla(nla, path)
            else:
                deferred.append((nla, path, predicate))
        self.deferred = deferred

    def append_nla(self, nla, path):
            pwd = self
            for step in path:
                if step in pwd:
                    pwd = pwd[step]
                else:
                    pwd = [x[1] for x in pwd['attrs']
                           if x[0] == step][0]['attrs']
            pwd.append(nla)

    def defer_nla(self, nla, path, predicate):
        self.deferred.append((nla, path, predicate))
        self.flush_deferred()
