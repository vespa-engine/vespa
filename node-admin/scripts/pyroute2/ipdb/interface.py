# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.
import time
import errno
import socket
import threading
import traceback
from pyroute2 import config
from pyroute2.common import basestring
from pyroute2.common import dqn2int
from pyroute2.netlink import NetlinkError
from pyroute2.netlink.rtnl.req import IPLinkRequest
from pyroute2.netlink.rtnl.ifinfmsg import IFF_MASK
from pyroute2.netlink.rtnl.ifinfmsg import ifinfmsg
from pyroute2.ipdb.transactional import Transactional
from pyroute2.ipdb.transactional import update
from pyroute2.ipdb.linkedset import LinkedSet
from pyroute2.ipdb.linkedset import IPaddrSet
from pyroute2.ipdb.common import CommitException
from pyroute2.ipdb.common import SYNC_TIMEOUT
from pyroute2.ipdb.common import compat


class Interface(Transactional):
    '''
    Objects of this class represent network interface and
    all related objects:
    * addresses
    * (todo) neighbors
    * (todo) routes

    Interfaces provide transactional model and can act as
    context managers. Any attribute change implicitly
    starts a transaction. The transaction can be managed
    with three methods:
    * review() -- review changes
    * rollback() -- drop all the changes
    * commit() -- try to apply changes

    If anything will go wrong during transaction commit,
    it will be rolled back authomatically and an
    exception will be raised. Failed transaction review
    will be attached to the exception.
    '''
    _fields_cmp = {'flags': lambda x, y: x & y & IFF_MASK == y & IFF_MASK}

    def __init__(self, ipdb, mode=None, parent=None, uid=None):
        '''
        Parameters:
        * ipdb -- ipdb() reference
        * mode -- transaction mode
        '''
        Transactional.__init__(self, ipdb, mode)
        self.cleanup = ('header',
                        'linkinfo',
                        'af_spec',
                        'attrs',
                        'event',
                        'map',
                        'stats',
                        'stats64',
                        '__align')
        self.ingress = None
        self.egress = None
        self._exists = False
        self._flicker = False
        self._exception = None
        self._tb = None
        self._virtual_fields = ('removal', 'flicker', 'state')
        self._xfields = {'common': [ifinfmsg.nla2name(i[0]) for i
                                    in ifinfmsg.nla_map]}
        self._xfields['common'].append('index')
        self._xfields['common'].append('flags')
        self._xfields['common'].append('mask')
        self._xfields['common'].append('change')
        self._xfields['common'].append('kind')
        self._xfields['common'].append('peer')
        self._xfields['common'].append('vlan_id')
        self._xfields['common'].append('bond_mode')

        for data in ('bridge_data',
                     'bond_data',
                     'tuntap_data',
                     'vxlan_data',
                     'gre_data',
                     'macvlan_data',
                     'macvtap_data'):
            msg = getattr(ifinfmsg.ifinfo, data)
            self._xfields['common'].extend([msg.nla2name(i[0]) for i
                                            in msg.nla_map])
        for ftype in self._xfields:
            self._fields += self._xfields[ftype]
        self._fields.extend(self._virtual_fields)
        self._load_event = threading.Event()
        self._linked_sets.add('ipaddr')
        self._linked_sets.add('ports')
        self._freeze = None
        # 8<-----------------------------------
        # local setup: direct state is required
        with self._direct_state:
            self['ipaddr'] = IPaddrSet()
            self['ports'] = LinkedSet()
            for i in self._fields:
                self[i] = None
            for i in ('state', 'change', 'mask'):
                del self[i]
        # 8<-----------------------------------

    def __hash__(self):
        return self['index']

    @property
    def if_master(self):
        '''
        [property] Link to the parent interface -- if it exists
        '''
        return self.get('master', None)

    def freeze(self):
        dump = self.dump()

        def cb(ipdb, msg, action):
            if msg.get('index', -1) == dump['index']:
                tr = self.load(dump)
                for _ in range(3):
                    try:
                        self.commit(transaction=tr)
                    except (CommitException, RuntimeError):
                        # ignore here both CommitExceptions
                        # and RuntimeErrors (aka rollback errors),
                        # since ususally it is a races between
                        # 3d party setup and freeze; just
                        # sliently try again for several times
                        continue
                    except NetlinkError:
                        # on the netlink errors just give up
                        pass
                    break

        self._freeze = cb
        self.ipdb.register_callback(self._freeze)
        return self

    def unfreeze(self):
        self.ipdb.unregister_callback(self._freeze)
        self._freeze = None
        return self

    def load(self, data):
        with self._write_lock:
            template = self.__class__(ipdb=self.ipdb, mode='snapshot')
            template.load_dict(data)
            return template

    def load_dict(self, data):
        with self._direct_state:
            for key in data:
                if key == 'ipaddr':
                    for addr in data[key]:
                        if isinstance(addr, basestring):
                            addr = (addr, )
                        self.add_ip(*addr)
                elif key == 'ports':
                    for port in data[key]:
                        self.add_port(port)
                elif key == 'neighbors':
                    # ignore neighbors on load
                    pass
                else:
                    self[key] = data[key]

    def load_netlink(self, dev):
        '''
        Update the interface info from RTM_NEWLINK message.

        This call always bypasses open transactions, loading
        changes directly into the interface data.
        '''
        with self._direct_state:
            self._exists = True
            self.nlmsg = dev
            for (name, value) in dev.items():
                self[name] = value
            for item in dev['attrs']:
                name, value = item[:2]
                norm = ifinfmsg.nla2name(name)
                self[norm] = value
            # load interface kind
            linkinfo = dev.get_attr('IFLA_LINKINFO')
            if linkinfo is not None:
                kind = linkinfo.get_attr('IFLA_INFO_KIND')
                if kind is not None:
                    self['kind'] = kind
                    if kind == 'vlan':
                        data = linkinfo.get_attr('IFLA_INFO_DATA')
                        self['vlan_id'] = data.get_attr('IFLA_VLAN_ID')
                    if kind in ('vxlan', 'macvlan', 'macvtap', 'gre'):
                        data = linkinfo.get_attr('IFLA_INFO_DATA')
                        for nla in data.get('attrs', []):
                            norm = ifinfmsg.nla2name(nla[0])
                            self[norm] = nla[1]
                # get OVS master and override IFLA_MASTER value
                try:
                    data = linkinfo.get_attr('IFLA_INFO_DATA')
                    master = data.get_attr('IFLA_OVS_MASTER_IFNAME')
                    self['master'] = self.ipdb.interfaces[master].index
                except (AttributeError, KeyError):
                    pass
            # the rest is possible only when interface
            # is used in IPDB, not standalone
            if self.ipdb is not None:
                self['ipaddr'] = self.ipdb.ipaddr[self['index']]
                self['neighbors'] = self.ipdb.neighbors[self['index']]
            # finally, cleanup all not needed
            for item in self.cleanup:
                if item in self:
                    del self[item]

            self.sync()

    def sync(self):
        self._load_event.set()

    @update
    def add_ip(self, direct, ip, mask=None,
               brd=None, broadcast=None):
        '''
        Add IP address to an interface
        '''
        # split mask
        if mask is None:
            ip, mask = ip.split('/')
            if mask.find('.') > -1:
                mask = dqn2int(mask)
            else:
                mask = int(mask, 0)
        elif isinstance(mask, basestring):
            mask = dqn2int(mask)
        brd = brd or broadcast
        # FIXME: make it more generic
        # skip IPv6 link-local addresses
        if ip[:4] == 'fe80' and mask == 64:
            return self
        if not direct:
            transaction = self.last()
            transaction.add_ip(ip, mask, brd)
        else:
            self['ipaddr'].unlink((ip, mask))
            if brd is not None:
                raw = {'IFA_BROADCAST': brd}
                self['ipaddr'].add((ip, mask), raw=raw)
            else:
                self['ipaddr'].add((ip, mask))
        return self

    @update
    def del_ip(self, direct, ip, mask=None):
        '''
        Delete IP address from an interface
        '''
        if mask is None:
            ip, mask = ip.split('/')
            if mask.find('.') > -1:
                mask = dqn2int(mask)
            else:
                mask = int(mask, 0)
        if not direct:
            transaction = self.last()
            if (ip, mask) in transaction['ipaddr']:
                transaction.del_ip(ip, mask)
        else:
            self['ipaddr'].unlink((ip, mask))
            self['ipaddr'].remove((ip, mask))
        return self

    @update
    def add_port(self, direct, port):
        '''
        Add a slave port to a bridge or bonding
        '''
        if isinstance(port, Interface):
            port = port['index']
        if not direct:
            transaction = self.last()
            transaction.add_port(port)
        else:
            self['ports'].unlink(port)
            self['ports'].add(port)
        return self

    @update
    def del_port(self, direct, port):
        '''
        Remove a slave port from a bridge or bonding
        '''
        if isinstance(port, Interface):
            port = port['index']
        if not direct:
            transaction = self.last()
            if port in transaction['ports']:
                transaction.del_port(port)
        else:
            self['ports'].unlink(port)
            self['ports'].remove(port)
        return self

    def reload(self):
        '''
        Reload interface information
        '''
        countdown = 3
        while countdown:
            links = self.nl.get_links(self['index'])
            if links:
                self.load_netlink(links[0])
                break
            else:
                countdown -= 1
                time.sleep(1)
        return self

    def filter(self, ftype):
        ret = {}
        for key in self:
            if key in self._xfields[ftype]:
                ret[key] = self[key]
        return ret

    def commit(self, tid=None, transaction=None, rollback=False, newif=False):
        '''
        Commit transaction. In the case of exception all
        changes applied during commit will be reverted.
        '''
        error = None
        added = None
        removed = None
        drop = True
        if tid:
            transaction = self._transactions[tid]
        else:
            if transaction:
                drop = False
            else:
                transaction = self.last()

        wd = None
        with self._write_lock:
            # if the interface does not exist, create it first ;)
            if not self._exists:
                request = IPLinkRequest(self.filter('common'))

                # create watchdog
                wd = self.ipdb.watchdog(ifname=self['ifname'])

                newif = True
                try:
                    # 8<----------------------------------------------------
                    # ACHTUNG: hack for old platforms
                    if request.get('address', None) == '00:00:00:00:00:00':
                        del request['address']
                        del request['broadcast']
                    # 8<----------------------------------------------------
                    try:
                        self.nl.link('add', **request)
                    except NetlinkError as x:
                        # File exists
                        if x.code == errno.EEXIST:
                            # A bit special case, could be one of two cases:
                            #
                            # 1. A race condition between two different IPDB
                            #    processes
                            # 2. An attempt to create dummy0, gre0, bond0 when
                            #    the corrseponding module is not loaded. Being
                            #    loaded, the module creates a default interface
                            #    by itself, causing the request to fail
                            #
                            # The exception in that case can cause the DB
                            # inconsistence, since there can be queued not only
                            # the interface creation, but also IP address
                            # changes etc.
                            #
                            # So we ignore this particular exception and try to
                            # continue, as it is created by us.
                            pass

                        # Operation not supported
                        elif x.code == errno.EOPNOTSUPP and \
                                request.get('index', 0) != 0:
                            # ACHTUNG: hack for old platforms
                            request = IPLinkRequest({'ifname': self['ifname'],
                                                     'kind': self['kind'],
                                                     'index': 0})
                            self.nl.link('add', **request)
                        else:
                            raise
                except Exception as e:
                    # on failure, invalidate the interface and detach it
                    # from the parent
                    # 1. drop the IPRoute() link
                    self.nl = None
                    # 2. clean up ipdb
                    self.ipdb.detach(self['index'])
                    self.ipdb.detach(self['ifname'])
                    # 3. invalidate the interface
                    with self._direct_state:
                        for i in tuple(self.keys()):
                            del self[i]
                    # 4. the rest
                    self._mode = 'invalid'
                    self._exception = e
                    self._tb = traceback.format_exc()
                    # raise the exception
                    raise

        if wd is not None:
            wd.wait()

        # now we have our index and IP set and all other stuff
        snapshot = self.pick()

        try:
            removed = snapshot - transaction
            added = transaction - snapshot

            # 8<---------------------------------------------
            # Interface slaves
            self['ports'].set_target(transaction['ports'])

            for i in removed['ports']:
                # detach the port
                port = self.ipdb.interfaces[i]
                port.set_target('master', None)
                port.mirror_target('master', 'link')
                self.nl.link('set', index=port['index'], master=0)

            for i in added['ports']:
                # enslave the port
                port = self.ipdb.interfaces[i]
                port.set_target('master', self['index'])
                port.mirror_target('master', 'link')
                self.nl.link('set',
                             index=port['index'],
                             master=self['index'])

            if removed['ports'] or added['ports']:
                self.nl.get_links(*(removed['ports'] | added['ports']))
                self['ports'].target.wait(SYNC_TIMEOUT)
                if not self['ports'].target.is_set():
                    raise CommitException('ports target is not set')

                # RHEL 6.5 compat fix -- an explicit timeout
                # it gives a time for all the messages to pass
                compat.fix_timeout(1)

                # wait for proper targets on ports
                for i in list(added['ports']) + list(removed['ports']):
                    port = self.ipdb.interfaces[i]
                    target = port._local_targets['master']
                    target.wait(SYNC_TIMEOUT)
                    del port._local_targets['master']
                    del port._local_targets['link']
                    if not target.is_set():
                        raise CommitException('master target failed')
                    if i in added['ports']:
                        assert port.if_master == self['index']
                    else:
                        assert port.if_master != self['index']

            # 8<---------------------------------------------
            # Interface changes
            request = IPLinkRequest()
            for key in added:
                if key in self._xfields['common']:
                    request[key] = added[key]
            request['index'] = self['index']

            # apply changes only if there is something to apply
            if any([request[item] is not None for item in request
                    if item != 'index']):
                self.nl.link('set', **request)
                # hardcoded pause -- if the interface was moved
                # across network namespaces
                if 'net_ns_fd' in request:
                    while True:
                        # wait until the interface will disappear
                        # from the main network namespace
                        try:
                            self.nl.get_links(self['index'])
                        except NetlinkError as e:
                            if e.code == errno.ENODEV:
                                break
                            raise
                        except Exception:
                            raise
                    time.sleep(0.1)

            # 8<---------------------------------------------
            # IP address changes
            self['ipaddr'].set_target(transaction['ipaddr'])

            for i in removed['ipaddr']:
                # Ignore link-local IPv6 addresses
                if i[0][:4] == 'fe80' and i[1] == 64:
                    continue
                # When you remove a primary IP addr, all subnetwork
                # can be removed. In this case you will fail, but
                # it is OK, no need to roll back
                try:
                    self.nl.addr('delete', self['index'], i[0], i[1])
                except NetlinkError as x:
                    # bypass only errno 99, 'Cannot assign address'
                    if x.code != errno.EADDRNOTAVAIL:
                        raise
                except socket.error as x:
                    # bypass illegal IP requests
                    if not x.args[0].startswith('illegal IP'):
                        raise

            for i in added['ipaddr']:
                # Ignore link-local IPv6 addresses
                if i[0][:4] == 'fe80' and i[1] == 64:
                    continue
                # Try to fetch additional address attributes
                try:
                    kwarg = transaction.ipaddr[i]
                except KeyError:
                    kwarg = None
                self.nl.addr('add', self['index'], i[0], i[1],
                             **kwarg if kwarg else {})

                # 8<--------------------------------------
                # FIXME: kernel bug, sometimes `addr add` for
                # bond interfaces returns success, but does
                # really nothing

                if self['kind'] == 'bond':
                    while True:
                        try:
                            # dirtiest hack, but we have to use it here
                            time.sleep(0.1)
                            self.nl.addr('add', self['index'], i[0], i[1])
                        # continue to try to add the address
                        # until the kernel reports `file exists`
                        #
                        # a stupid solution, but must help
                        except NetlinkError as e:
                            if e.code == errno.EEXIST:
                                break
                            else:
                                raise
                        except Exception:
                            raise
                # 8<--------------------------------------

            if removed['ipaddr'] or added['ipaddr']:
                # 8<--------------------------------------
                # bond and bridge interfaces do not send
                # IPv6 address updates, when are down
                #
                # beside of that, bridge interfaces are
                # down by default, so they never send
                # address updates from beginning
                #
                # so if we need, force address load
                #
                # FIXME: probably, we should handle other
                # types as well
                if self['kind'] in ('bond', 'bridge', 'veth'):
                    self.nl.get_addr()
                # 8<--------------------------------------
                self['ipaddr'].target.wait(SYNC_TIMEOUT)
                if not self['ipaddr'].target.is_set():
                    raise CommitException('ipaddr target is not set')

            # 8<---------------------------------------------
            # reload interface to hit targets
            if transaction._targets:
                try:
                    self.reload()
                except NetlinkError as e:
                    if e.code == errno.ENODEV:  # No such device
                        if ('net_ns_fd' in added) or \
                                ('net_ns_pid' in added):
                            # it means, that the device was moved
                            # to another netns; just give up
                            if drop:
                                self.drop(transaction)
                                return self

            # wait for targets
            transaction._wait_all_targets()

            # 8<---------------------------------------------
            # Interface removal
            if added.get('removal') or \
                    added.get('flicker') or\
                    (newif and rollback):
                wd = self.ipdb.watchdog(action='RTM_DELLINK',
                                        ifname=self['ifname'])
                if added.get('flicker'):
                    self._flicker = True
                self.nl.link('delete', **self)
                wd.wait()
                if added.get('flicker'):
                    self._exists = False
                if added.get('removal'):
                    self._mode = 'invalid'
                if drop:
                    self.drop(transaction)
                return self
            # 8<---------------------------------------------

            # Iterate callback chain
            for ch in self._commit_hooks:
                # An exception will rollback the transaction
                ch(self.dump(), snapshot.dump(), transaction.dump())
            # 8<---------------------------------------------

        except Exception as e:
            # something went wrong: roll the transaction back
            if not rollback:
                ret = self.commit(transaction=snapshot,
                                  rollback=True,
                                  newif=newif)
                # if some error was returned by the internal
                # closure, substitute the initial one
                if isinstance(ret, Exception):
                    error = ret
                else:
                    error = e
                    error.traceback = traceback.format_exc()
            elif isinstance(e, NetlinkError) and \
                    getattr(e, 'code', 0) == errno.EPERM:
                # It is <Operation not permitted>, catched in
                # rollback. So return it -- see ~5 lines above
                e.traceback = traceback.format_exc()
                return e
            else:
                # somethig went wrong during automatic rollback.
                # that's the worst case, but it is still possible,
                # since we have no locks on OS level.
                self['ipaddr'].set_target(None)
                self['ports'].set_target(None)
                # reload all the database -- it can take a long time,
                # but it is required since we have no idea, what is
                # the result of the failure
                #
                # ACHTUNG: database reload is asynchronous, so after
                # getting RuntimeError() from commit(), take a seat
                # and rest for a while. It is an extremal case, it
                # should not became at all, and there is no sync.
                self.nl.get_links()
                self.nl.get_addr()
                x = RuntimeError()
                x.cause = e
                x.traceback = traceback.format_exc()
                raise x

        # if it is not a rollback turn
        if drop and not rollback:
            # drop last transaction in any case
            self.drop(transaction)

        # raise exception for failed transaction
        if error is not None:
            error.transaction = transaction
            raise error

        time.sleep(config.commit_barrier)
        return self

    def up(self):
        '''
        Shortcut: change the interface state to 'up'.
        '''
        if self['flags'] is None:
            self['flags'] = 1
        else:
            self['flags'] |= 1
        return self

    def down(self):
        '''
        Shortcut: change the interface state to 'down'.
        '''
        if self['flags'] is None:
            self['flags'] = 0
        else:
            self['flags'] &= ~(self['flags'] & 1)
        return self

    def remove(self):
        '''
        Mark the interface for removal
        '''
        self['removal'] = True
        return self

    def shadow(self):
        '''
        Remove the interface from the OS, but leave it in the
        database. When one will try to re-create interface with
        the same name, all the old saved attributes will apply
        to the new interface, incl. MAC-address and even the
        interface index. Please be aware, that the interface
        index can be reused by OS while the interface is "in the
        shadow state", in this case re-creation will fail.
        '''
        self['flicker'] = True
        return self
