# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.
import select
import socket
import struct
import threading
from pyroute2.iproute import IPRoute
try:
    from Queue import Queue
except ImportError:
    from queue import Queue


class Server(object):

    def __init__(self, addr='0.0.0.0', port=3546):
        self.addr = addr
        self.port = port

    def run(self):
        nat = {}
        clients = []

        srv = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        srv.bind((self.addr, self.port))
        ipr = IPRoute()
        ipr.bind()

        poll = select.poll()
        poll.register(ipr, select.POLLIN | select.POLLPRI)
        poll.register(srv, select.POLLIN | select.POLLPRI)

        while True:
            events = poll.poll()
            for (fd, event) in events:
                if fd == ipr.fileno():
                    bufsize = ipr.getsockopt(socket.SOL_SOCKET,
                                             socket.SO_RCVBUF) // 2
                    data = ipr.recv(bufsize)
                    cookie = struct.unpack('I', data[8:12])[0]
                    if cookie == 0:
                        for address in clients:
                            srv.sendto(data, address)
                    else:
                        srv.sendto(data, nat[cookie])
                else:
                    data, address = srv.recvfrom(16384)
                    if data is None:
                        clients.remove(address)
                        continue
                    cookie = struct.unpack('I', data[8:12])[0]
                    nat[cookie] = address
                    ipr.sendto(data, (0, 0))


class Client(IPRoute):

    def __init__(self, addr):
        IPRoute.__init__(self)
        self.proxy = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.proxy.bind(('0.0.0.0', 3547))
        self.proxy_addr = addr
        self.proxy_queue = Queue()

        def recv():
            while True:
                (data, addr) = self.proxy.recvfrom(16384)
                self.proxy_queue.put(data)

        self.pthread = threading.Thread(target=recv)
        self.pthread.setDaemon(True)
        self.pthread.start()

        def sendto(buf, *argv, **kwarg):
            return self.proxy.sendto(buf, (self.proxy_addr, 3546))

        def recv(*argv, **kwarg):
            return self.proxy_queue.get()

        self._sendto = sendto
        self._recv = recv

    def close(self):
        self.proxy.close()
        self.recv = lambda *x, **y: None
        self.proxy_queue.put(None)
