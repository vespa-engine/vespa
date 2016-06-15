# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.
'''
'''
import uuid
import threading
from pyroute2.common import Dotkeys
from pyroute2.ipdb.common import SYNC_TIMEOUT
from pyroute2.ipdb.common import CommitException
from pyroute2.ipdb.common import DeprecationException
from pyroute2.ipdb.linkedset import LinkedSet


class State(object):

    def __init__(self, lock=None):
        self.lock = lock or threading.Lock()
        self.flag = 0

    def acquire(self):
        self.lock.acquire()
        self.flag += 1

    def release(self):
        assert self.flag > 0
        self.flag -= 1
        self.lock.release()

    def is_set(self):
        return self.flag

    def __enter__(self):
        self.acquire()
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        self.release()


def update(f):
    def decorated(self, *argv, **kwarg):
        # obtain update lock
        ret = None
        tid = None
        direct = True
        with self._write_lock:
            dcall = kwarg.pop('direct', False)
            if dcall:
                self._direct_state.acquire()

            direct = self._direct_state.is_set()
            if not direct:
                # 1. begin transaction for 'direct' type
                if self._mode == 'direct':
                    tid = self.begin()
                # 2. begin transaction, if there is none
                elif self._mode == 'implicit':
                    if not self._tids:
                        self.begin()
                # 3. require open transaction for 'explicit' type
                elif self._mode == 'explicit':
                    if not self._tids:
                        raise TypeError('start a transaction first')
                # 4. transactions can not require transactions :)
                elif self._mode == 'snapshot':
                    direct = True
                # do not support other modes
                else:
                    raise TypeError('transaction mode not supported')
                # now that the transaction _is_ open
            ret = f(self, direct, *argv, **kwarg)

            if dcall:
                self._direct_state.release()

        if tid:
            # close the transaction for 'direct' type
            self.commit(tid)

        return ret
    decorated.__doc__ = f.__doc__
    return decorated


class Transactional(Dotkeys):
    '''
    Utility class that implements common transactional logic.
    '''
    _fields_cmp = {}

    def __init__(self, ipdb=None, mode=None, parent=None, uid=None):
        #
        if ipdb is not None:
            self.nl = ipdb.nl
            self.ipdb = ipdb
        else:
            self.nl = None
            self.ipdb = None
        #
        self._parent = None
        if parent is not None:
            self._mode = mode or parent._mode
            self._parent = parent
        elif ipdb is not None:
            self._mode = mode or ipdb.mode
        else:
            self._mode = mode or 'implicit'
        #
        self.nlmsg = None
        self.uid = uid or uuid.uuid4()
        self.last_error = None
        self._commit_hooks = []
        self._fields = []
        self._sids = []
        self._ts = threading.local()
        self._snapshots = {}
        self._targets = {}
        self._local_targets = {}
        self._write_lock = threading.RLock()
        self._direct_state = State(self._write_lock)
        self._linked_sets = set()

    @property
    def _tids(self):
        if not hasattr(self._ts, 'tids'):
            self._ts.tids = []
        return self._ts.tids

    @property
    def _transactions(self):
        if not hasattr(self._ts, 'transactions'):
            self._ts.transactions = {}
        return self._ts.transactions

    def register_callback(self, callback):
        raise DeprecationException("deprecated since 0.2.15;"
                                   "use `register_commit_hook()`")

    def register_commit_hook(self, hook):
        # FIXME: write docs
        self._commit_hooks.append(hook)

    def unregister_callback(self, callback):
        raise DeprecationException("deprecated since 0.2.15;"
                                   "use `unregister_commit_hook()`")

    def unregister_commit_hook(self, hook):
        # FIXME: write docs
        with self._write_lock:
            for cb in tuple(self._commit_hooks):
                if hook == cb:
                    self._commit_hooks.pop(self._commit_hooks.index(cb))

    def pick(self, detached=True, uid=None, parent=None, forge_tids=False):
        '''
        Get a snapshot of the object. Can be of two
        types:
        * detached=True -- (default) "true" snapshot
        * detached=False -- keep ip addr set updated from OS

        Please note, that "updated" doesn't mean "in sync".
        The reason behind this logic is that snapshots can be
        used as transactions.
        '''
        with self._write_lock:
            res = self.__class__(ipdb=self.ipdb,
                                 mode='snapshot',
                                 parent=parent,
                                 uid=uid)
            for (key, value) in self.items():
                if key in self._fields:
                    if isinstance(value, Transactional):
                        t = value.pick(detached=detached,
                                       uid=res.uid,
                                       parent=self)
                        if forge_tids:
                            # forge the transaction for nested objects
                            value._transactions[res.uid] = t
                            value._tids.append(res.uid)
                        res[key] = t
                    else:
                        res[key] = self[key]
            for key in self._linked_sets:
                res[key] = LinkedSet(self[key])
                if not detached:
                    self[key].connect(res[key])
            return res

    def __enter__(self):
        # FIXME: use a bitmask?
        if self._mode not in ('implicit', 'explicit'):
            raise TypeError('context managers require a transactional mode')
        if not self._tids:
            self.begin()
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        # apply transaction only if there was no error
        if exc_type is None:
            try:
                self.commit()
            except Exception as e:
                self.last_error = e
                raise

    def __repr__(self):
        res = {}
        for i in self:
            if self[i] is not None:
                res[i] = self[i]
        return res.__repr__()

    def __sub__(self, vs):
        res = self.__class__(ipdb=self.ipdb, mode='snapshot')
        with self._direct_state:
            # simple keys
            for key in self:
                if (key in self._fields) and \
                        ((key not in vs) or (self[key] != vs[key])):
                    res[key] = self[key]
        for key in self._linked_sets:
            diff = LinkedSet(self[key] - vs[key])
            if diff:
                res[key] = diff
        return res

    def dump(self, not_none=True):
        with self._write_lock:
            res = {}
            for key in self:
                if self[key] is not None and key[0] != '_':
                    if isinstance(self[key], Transactional):
                        res[key] = self[key].dump()
                    elif isinstance(self[key], LinkedSet):
                        res[key] = tuple(self[key])
                    else:
                        res[key] = self[key]
            return res

    def load(self, data):
        pass

    def commit(self, *args, **kwarg):
        pass

    def last_snapshot_id(self):
        return self._sids[-1]

    def revert(self, sid):
        with self._write_lock:
            self._transactions[sid] = self._snapshots[sid]
            self._tids.append(sid)
            self._sids.remove(sid)
            del self._snapshots[sid]
            return self

    def snapshot(self):
        '''
        Create new snapshot
        '''
        return self._begin(mapping=self._snapshots,
                           ids=self._sids,
                           detached=True)

    def begin(self):
        '''
        Start new transaction
        '''
        if self._parent is not None:
            self._parent.begin()
        else:
            return self._begin(mapping=self._transactions,
                               ids=self._tids,
                               detached=False)

    def _begin(self, mapping, ids, detached):
        # keep snapshot's ip addr set updated from the OS
        # it is required by the commit logic
        if (self.ipdb is not None) and self.ipdb._stop:
            raise RuntimeError("Can't start transaction on released IPDB")
        t = self.pick(detached=detached, forge_tids=True)
        mapping[t.uid] = t
        ids.append(t.uid)
        return t.uid

    def last_snapshot(self):
        if not self._sids:
            raise TypeError('create a snapshot first')
        return self._snapshots[self._sids[-1]]

    def last(self):
        '''
        Return last open transaction
        '''
        with self._write_lock:
            if not self._tids:
                raise TypeError('start a transaction first')

            return self._transactions[self._tids[-1]]

    def review(self):
        '''
        Review last open transaction
        '''
        if not self._tids:
            raise TypeError('start a transaction first')

        with self._write_lock:
            added = self.last() - self
            removed = self - self.last()
            for key in self._linked_sets:
                added['-%s' % (key)] = removed[key]
                added['+%s' % (key)] = added[key]
                del added[key]
            return added

    def drop(self, tid=None):
        '''
        Drop a transaction.
        '''
        with self._write_lock:
            if isinstance(tid, Transactional):
                tid = tid.uid
            elif tid is None:
                tid = self._tids[-1]
            self._tids.remove(tid)
            del self._transactions[tid]
            for (key, value) in self.items():
                if isinstance(value, Transactional):
                    try:
                        value.drop(tid)
                    except KeyError:
                        pass

    @update
    def __setitem__(self, direct, key, value):
        with self._write_lock:
            if not direct:
                # automatically set target on the last transaction,
                # which must be started prior to that call
                transaction = self.last()
                transaction[key] = value
                transaction._targets[key] = threading.Event()
            else:
                # set the item
                Dotkeys.__setitem__(self, key, value)

                # update on local targets
                if key in self._local_targets:
                    func = self._fields_cmp.get(key, lambda x, y: x == y)
                    if func(value, self._local_targets[key].value):
                        self._local_targets[key].set()

                # cascade update on nested targets
                for tn in tuple(self._transactions.values()):
                    if (key in tn._targets) and (key in tn):
                        if self._fields_cmp.\
                                get(key, lambda x, y: x == y)(value, tn[key]):
                            tn._targets[key].set()

    @update
    def __delitem__(self, direct, key):
        with self._write_lock:
            # firstly set targets
            self[key] = None

            # then continue with delete
            if not direct:
                transaction = self.last()
                if key in transaction:
                    del transaction[key]
            else:
                Dotkeys.__delitem__(self, key)

    def option(self, key, value):
        self[key] = value
        return self

    def unset(self, key):
        del self[key]
        return self

    def _wait_all_targets(self):
        for key, target in self._targets.items():
            if key not in self._virtual_fields:
                target.wait(SYNC_TIMEOUT)
                if not target.is_set():
                    raise CommitException('target %s is not set' % key)

    def set_target(self, key, value):
        self._local_targets[key] = threading.Event()
        self._local_targets[key].value = value

    def mirror_target(self, key_from, key_to):
        self._local_targets[key_to] = self._local_targets[key_from]

    def set_item(self, key, value):
        with self._direct_state:
            self[key] = value

    def del_item(self, key):
        with self._direct_state:
            del self[key]
