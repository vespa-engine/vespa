# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.
import struct
from ctypes import Structure
from ctypes import addressof
from ctypes import string_at
from ctypes import sizeof
from ctypes import c_ushort
from ctypes import c_ubyte
from ctypes import c_uint
from ctypes import c_void_p
from socket import socket
from socket import htons
from socket import AF_PACKET
from socket import SOCK_RAW
from socket import SOL_SOCKET
from pyroute2 import IPRoute

ETH_P_ALL = 3
SO_ATTACH_FILTER = 26


class sock_filter(Structure):
    _fields_ = [('code', c_ushort),  # u16
                ('jt', c_ubyte),     # u8
                ('jf', c_ubyte),     # u8
                ('k', c_uint)]       # u32


class sock_fprog(Structure):
    _fields_ = [('len', c_ushort),
                ('filter', c_void_p)]


def compile_bpf(code):
    ProgramType = sock_filter * len(code)
    program = ProgramType(*[sock_filter(*line) for line in code])
    sfp = sock_fprog(len(code), addressof(program[0]))
    return string_at(addressof(sfp), sizeof(sfp)), program


class RawSocket(socket):

    fprog = None

    def __init__(self, ifname, bpf=None):
        self.ifname = ifname
        # lookup the interface details
        with IPRoute() as ip:
            for link in ip.get_links():
                if link.get_attr('IFLA_IFNAME') == ifname:
                    break
            else:
                raise IOError(2, 'Link not found')
        self.l2addr = link.get_attr('IFLA_ADDRESS')
        self.ifindex = link['index']
        # bring up the socket
        socket.__init__(self, AF_PACKET, SOCK_RAW, htons(ETH_P_ALL))
        socket.bind(self, (self.ifname, ETH_P_ALL))
        if bpf:
            fstring, self.fprog = compile_bpf(bpf)
            socket.setsockopt(self, SOL_SOCKET, SO_ATTACH_FILTER, fstring)

    def csum(self, data):
        if len(data) % 2:
            data += b'\x00'
        csum = sum([struct.unpack('>H', data[x*2:x*2+2])[0] for x
                    in range(len(data)//2)])
        csum = (csum >> 16) + (csum & 0xffff)
        csum += csum >> 16
        return ~csum & 0xffff
