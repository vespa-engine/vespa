# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.
'''
'''
import threading


class LinkedSet(set):
    '''
    Utility class, used by `Interface` to track ip addresses
    and ports. Called "linked" as it automatically updates all
    instances, linked with it.

    Target filter is a function, that returns `True` if a set
    member should be counted in target checks (target methods
    see below), or `False` if it should be ignored.
    '''
    def target_filter(self, x):
        return True

    def __init__(self, *argv, **kwarg):
        set.__init__(self, *argv, **kwarg)
        self.lock = threading.RLock()
        self.target = threading.Event()
        self._ct = None
        self.raw = {}
        self.links = []
        self.exclusive = set()

    def __getitem__(self, key):
        return self.raw[key]

    def set_target(self, value):
        '''
        Set target state for the object and clear the target
        event. Once the target is reached, the event will be
        set, see also: `check_target()`

        Args:
            - value (set): the target state to compare with
        '''
        with self.lock:
            if value is None:
                self._ct = None
                self.target.clear()
            else:
                self._ct = set(value)
                self.target.clear()
                # immediately check, if the target already
                # reached -- otherwise you will miss the
                # target forever
                self.check_target()

    def check_target(self):
        '''
        Check the target state and set the target event in the
        case the state is reached. Called from mutators, `add()`
        and `remove()`
        '''
        with self.lock:
            if self._ct is not None:
                if set(filter(self.target_filter, self)) == \
                        set(filter(self.target_filter, self._ct)):
                    self._ct = None
                    self.target.set()

    def add(self, key, raw=None, cascade=False):
        '''
        Add an item to the set and all connected instances,
        check the target state.

        Args:
            - key: any hashable object
            - raw (optional): raw representation of the object

        Raw representation is not required. It can be used, e.g.,
        to store RTM_NEWADDR RTNL messages along with
        human-readable ip addr representation.
        '''
        with self.lock:
            if cascade and (key in self.exclusive):
                return
            if key not in self:
                self.raw[key] = raw
                set.add(self, key)
                for link in self.links:
                    link.add(key, raw, cascade=True)
            self.check_target()

    def remove(self, key, raw=None, cascade=False):
        '''
        Remove an item from the set and all connected instances,
        check the target state.
        '''
        with self.lock:
            if cascade and (key in self.exclusive):
                return
            set.remove(self, key)
            for link in self.links:
                if key in link:
                    link.remove(key, cascade=True)
            self.check_target()

    def unlink(self, key):
        '''
        Exclude key from cascade updates.
        '''
        self.exclusive.add(key)

    def relink(self, key):
        '''
        Do not ignore key on cascade updates.
        '''
        self.exclusive.remove(key)

    def connect(self, link):
        '''
        Connect a LinkedSet instance to this one. Connected
        sets will be updated together with this instance.
        '''
        assert isinstance(link, LinkedSet)
        self.links.append(link)

    def __repr__(self):
        return repr(list(self))


class IPaddrSet(LinkedSet):
    '''
    LinkedSet child class with different target filter. The
    filter ignores link local IPv6 addresses when sets and checks
    the target.
    '''
    def target_filter(self, x):
        return not ((x[0][:4] == 'fe80') and (x[1] == 64))
