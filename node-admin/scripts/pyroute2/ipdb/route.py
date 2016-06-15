# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.
import logging
import threading
from socket import AF_UNSPEC
from pyroute2.common import basestring
from pyroute2.netlink import nlmsg
from pyroute2.netlink.rtnl.rtmsg import rtmsg
from pyroute2.netlink.rtnl.req import IPRouteRequest
from pyroute2.ipdb.transactional import Transactional


class Metrics(Transactional):

    def __init__(self, *argv, **kwarg):
        Transactional.__init__(self, *argv, **kwarg)
        self._fields = [rtmsg.metrics.nla2name(i[0]) for i
                        in rtmsg.metrics.nla_map]


class RouteKey(dict):
    '''
    Construct from a netlink message a key that can be used
    to locate the route in the table
    '''
    def __init__(self, msg):
        # calculate dst
        if msg.get_attr('RTA_DST', None) is not None:
            dst = '%s/%s' % (msg.get_attr('RTA_DST'),
                             msg['dst_len'])
        else:
            dst = 'default'
        self['dst'] = dst
        # use output | input interfaces as key also
        for key in ('oif', 'iif'):
            value = msg.get_attr(msg.name2nla(key))
            if value:
                self[key] = value


class Route(Transactional):
    '''
    Persistent transactional route object
    '''

    def __init__(self, ipdb, mode=None, parent=None, uid=None):
        Transactional.__init__(self, ipdb, mode, parent, uid)
        self._exists = False
        self._load_event = threading.Event()
        self._fields = [rtmsg.nla2name(i[0]) for i in rtmsg.nla_map]
        self._fields.append('flags')
        self._fields.append('src_len')
        self._fields.append('dst_len')
        self._fields.append('table')
        self._fields.append('removal')
        self.cleanup = ('attrs',
                        'header',
                        'event')
        with self._direct_state:
            self['metrics'] = Metrics(parent=self)

    def load_netlink(self, msg):
        with self._direct_state:
            self._exists = True
            self.update(msg)

            # re-init metrics
            metrics = self.get('metrics', Metrics(parent=self))
            with metrics._direct_state:
                for metric in tuple(metrics.keys()):
                    del metrics[metric]
            self['metrics'] = metrics

            # merge key
            for (name, value) in msg['attrs']:
                norm = rtmsg.nla2name(name)
                # normalize RTAX
                if norm == 'metrics':
                    with self['metrics']._direct_state:
                        for (rtax, rtax_value) in value['attrs']:
                            rtax_norm = rtmsg.metrics.nla2name(rtax)
                            self['metrics'][rtax_norm] = rtax_value
                else:
                    self[norm] = value

            if msg.get_attr('RTA_DST', None) is not None:
                dst = '%s/%s' % (msg.get_attr('RTA_DST'),
                                 msg['dst_len'])
            else:
                dst = 'default'
            self['dst'] = dst
            # finally, cleanup all not needed
            for item in self.cleanup:
                if item in self:
                    del self[item]

            self.sync()

    def sync(self):
        self._load_event.set()

    def reload(self):
        # do NOT call get_routes() here, it can cause race condition
        self._load_event.wait()
        return self

    def commit(self, tid=None, transaction=None, rollback=False):
        self._load_event.clear()
        error = None

        if tid:
            transaction = self._transactions[tid]
        else:
            transaction = transaction or self.last()

        # create a new route
        if not self._exists:
            try:
                self.nl.route('add', **IPRouteRequest(self))
            except Exception:
                self.nl = None
                self.ipdb.routes.remove(self)
                raise

        # work on existing route
        snapshot = self.pick()
        try:
            # route set
            request = IPRouteRequest(transaction - snapshot)
            if any([request[x] not in (None, {'attrs': []}) for x in request]):
                self.nl.route('set', **IPRouteRequest(transaction))

            if transaction.get('removal'):
                self.nl.route('delete', **IPRouteRequest(snapshot))

        except Exception as e:
            if not rollback:
                ret = self.commit(transaction=snapshot, rollback=True)
                if isinstance(ret, Exception):
                    error = ret
                else:
                    error = e
            else:
                self.drop()
                x = RuntimeError()
                x.cause = e
                raise x

        if not rollback:
            self.drop()

        if error is not None:
            error.transaction = transaction
            raise error

        if not rollback:
            self.reload()

        return self

    def remove(self):
        self['removal'] = True
        return self


class RoutingTable(object):

    def __init__(self, ipdb, prime=None):
        self.ipdb = ipdb
        self.records = prime or []

    def __repr__(self):
        return repr(self.records)

    def __len__(self):
        return len(self.records)

    def __iter__(self):
        for record in tuple(self.records):
            yield record

    def keys(self, key='dst'):
        return [x[key] for x in self.records]

    def describe(self, target, forward=True):
        if isinstance(target, int):
            return {'route': self.records[target],
                    'index': target}
        if isinstance(target, basestring):
            target = {'dst': target}
        if not isinstance(target, dict):
            raise TypeError('unsupported key type')

        for record in self.records:
            for key in target:
                # skip non-existing keys
                #
                # it's a hack, but newly-created routes
                # don't contain all the fields that are
                # in the netlink message
                if record.get(key) is None:
                    continue
                # if any key doesn't match
                if target[key] != record[key]:
                    break
            else:
                # if all keys match
                return {'route': record,
                        'index': self.records.index(record)}

        if not forward:
            raise KeyError('route not found')

        # split masks
        if target.get('dst', '').find('/') >= 0:
            dst = target['dst'].split('/')
            target['dst'] = dst[0]
            target['dst_len'] = int(dst[1])

        if target.get('src', '').find('/') >= 0:
            src = target['src'].split('/')
            target['src'] = src[0]
            target['src_len'] = int(src[1])

        # load and return the route, if exists
        route = Route(self.ipdb)
        route.load_netlink(self.ipdb.nl.get_routes(**target)[0])
        return {'route': route,
                'index': None}

    def __delitem__(self, key):
        self.records.pop(self.describe(key, forward=False)['index'])

    def __setitem__(self, key, value):
        try:
            record = self.describe(key, forward=False)
        except KeyError:
            record = {'route': Route(self.ipdb),
                      'index': None}

        if isinstance(value, nlmsg):
            record['route'].load_netlink(value)
        elif isinstance(value, Route):
            record['route'] = value
        elif isinstance(value, dict):
            with record['route']._direct_state:
                record['route'].update(value)

        if record['index'] is None:
            self.records.append(record['route'])
        else:
            self.records[record['index']] = record['route']

    def __getitem__(self, key):
        return self.describe(key, forward=True)['route']

    def __contains__(self, key):
        try:
            self.describe(key, forward=False)
            return True
        except KeyError:
            return False


class RoutingTableSet(object):

    def __init__(self, ipdb):
        self.ipdb = ipdb
        self.tables = {254: RoutingTable(self.ipdb)}

    def add(self, spec=None, **kwarg):
        '''
        Create a route from a dictionary
        '''
        spec = spec or kwarg
        table = spec.get('table', 254)
        assert 'dst' in spec
        if table not in self.tables:
            self.tables[table] = RoutingTable(self.ipdb)
        route = Route(self.ipdb)
        metrics = spec.pop('metrics', {})
        route.update(spec)
        route.metrics.update(metrics)
        self.tables[table][route['dst']] = route
        route.begin()
        return route

    def load_netlink(self, msg):
        '''
        Loads an existing route from a rtmsg
        '''
        table = msg.get('table', 254)
        # construct a key
        # FIXME: temporary solution
        # FIXME: can `Route()` be used as a key?
        key = RouteKey(msg)

        # RTM_DELROUTE
        if msg['event'] == 'RTM_DELROUTE':
            try:
                # locate the record
                record = self.tables[table][key]
                # delete the record
                del self.tables[table][key]
                # sync ???
                record.sync()
            except Exception as e:
                logging.debug(e)
                logging.debug(msg)
            return

        # RTM_NEWROUTE
        if table not in self.tables:
            self.tables[table] = RoutingTable(self.ipdb)
        self.tables[table][key] = msg
        return self.tables[table][key]

    def remove(self, route, table=None):
        if isinstance(route, Route):
            table = route.get('table', 254)
            route = route.get('dst', 'default')
        else:
            table = table or 254
        del self.tables[table][route]

    def describe(self, spec, table=254):
        return self.tables[table].describe(spec)

    def get(self, dst, table=None):
        table = table or 254
        return self.tables[table][dst]

    def keys(self, table=254, family=AF_UNSPEC):
        return [x['dst'] for x in self.tables[table]
                if (x['family'] == family)
                or (family == AF_UNSPEC)]

    def has_key(self, key, table=254):
        return key in self.tables[table]

    def __contains__(self, key):
        return key in self.tables[254]

    def __getitem__(self, key):
        return self.get(key)

    def __setitem__(self, key, value):
        assert key == value['dst']
        return self.add(value)

    def __delitem__(self, key):
        return self.remove(key)

    def __repr__(self):
        return repr(self.tables[254])
