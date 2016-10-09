# Created by ay27 at 16/10/1
import socket
import threading
from abc import abstractmethod

import uuid

import select

__all__ = ['User', 'MsgHandler', 'API']

_mcast_addr = ('224.168.2.9', 12345)

# login(logout) mac ip port name
_LOGIN_MSG = 'login\0%s\0%s\0%d\0%s'
_LOGOUT_MSG = 'login\0%s\0%s\0%d\0%s'

# userlist mac ip port name
_USERLIST = 'userlist\0%s\0%s\0%d\0%s'
_SEND_MSG = 'msg\0%s\0%s\0%d\0%s\0%s'


def _get_local_addr():
    mac = uuid.UUID(int=uuid.getnode()).hex[-12:]
    mac = ":".join([mac[e:e + 2] for e in range(0, 11, 2)])
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.settimeout(1)
    s.connect(("baidu.com", 80))
    ip = s.getsockname()[0]
    s.close()
    return mac, ip


class User:
    def __init__(self, name, mac, addr):
        self.mac = mac
        self.addr = addr
        self.name = name

    def __str__(self):
        return '%s %s %d %s' % (self.mac, self.addr[0], self.addr[1], self.name)


class MsgHandler:
    @abstractmethod
    def update_user_list(self, user_list):
        pass

    @abstractmethod
    def recv_msg(self, user, msg):
        pass


class API:
    def __init__(self, handler):
        self._handler = handler

        self.user_list = []

        self.mac, self.ip = _get_local_addr()

        self.udp_channel = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

        self.mcast_channel = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        self.mcast_channel.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.mcast_channel.bind(('', _mcast_addr[1]))
        self.mcast_channel.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 255)
        self.mcast_channel.setsockopt(socket.IPPROTO_IP,  # 告诉内核把自己加入指定的多播组，组地址由第三个参数指定
                                      socket.IP_ADD_MEMBERSHIP,
                                      socket.inet_aton(_mcast_addr[0]) + socket.inet_aton('0.0.0.0'))

        self.RUNNING = True
        self.mcast_thread = threading.Thread(target=self.mcast_recving)
        self.mcast_thread.start()
        self.udp_thread = threading.Thread(target=self.udp_recving)
        self.udp_thread.start()

    def udp_recving(self):
        while self.RUNNING:
            data, addr = self.udp_channel.recvfrom(1024)
            self.parse(data.decode('utf-8'), addr)

    def mcast_recving(self):
        while self.RUNNING:
            data, addr = self.mcast_channel.recvfrom(1024)
            self.parse(data.decode('utf-8'), addr)

    def parse(self, data, addr):
        datas = data.split('\0')

        # if ip is myself, drop it
        if datas[2] == self.ip:
            return

        # print('parse %s' % data)

        if datas[0] == 'login':
            new_user = True
            for u in self.user_list:
                if u.addr == addr:
                    u.name = datas[0]
                    u.mac = datas[1]
                    new_user = False
                    break
            if new_user:
                self.user_list.append(User(datas[4], datas[1], (datas[2], int(datas[3]))))
            self._handler.update_user_list(self.user_list)
        if datas[0] == 'logout':
            u = list(filter(lambda u: datas[1] == u.mac and datas[2] == u.addr[0] == addr[0] and datas[4] == u.name,
                            self.user_list))
            if u is not None and len(u) == 1:
                self.user_list.remove(u)
            self._handler.update_user_list(self.user_list)
        if datas[0] == 'userlist':
            u = list(filter(lambda u: datas[1] == u.mac and datas[2] == u.addr[0] == addr[0] and datas[4] == u.name,
                            self.user_list))
            if u is not None and len(u) == 1:
                self.login(self.name)
        if datas[0] == 'msg':
            u = list(filter(lambda u: datas[1] == u.mac and datas[2] == u.addr[0] == addr[0]
                                      and datas[4] == u.name, self.user_list))
            if u is not None and len(u) == 1:
                self._handler.recv_msg(u, datas[5])

    def login(self, name):
        if isinstance(name, str):
            self.name = name
        elif isinstance(name, bytes):
            self.name = name.decode(encoding='utf-8')
        else:
            raise ValueError('name must be a str or bytes')
        if not self.name.endswith(self.mac):
            self.name += self.mac
        self.udp_channel.sendto('test'.encode(), ('0.0.0.0', 1234))
        self.mcast_channel.sendto(self._fill(_LOGIN_MSG), _mcast_addr)

    def logout(self):
        self.mcast_channel.sendto(self._fill(_LOGOUT_MSG), _mcast_addr)

    def update_user_list(self):
        self.mcast_channel.sendto(self._fill(_USERLIST), _mcast_addr)

    def _fill(self, msg_template, content=None):
        if content is None:
            return (msg_template % (self.mac, self.ip, self.udp_channel.getsockname()[1], self.name)).encode('utf-8')
        else:
            return (msg_template % (self.mac, self.ip, self.udp_channel.getsockname()[1], self.name, content)).encode(
                    'utf-8')

    def send_msg(self, msg, user):
        if isinstance(msg, str):
            data = msg
        elif isinstance(msg, bytes):
            data = msg.decode(encoding='utf-8')
        else:
            raise ValueError('msg must be string or bytes')
        self.udp_channel.sendto(self._fill(_SEND_MSG, data), user.addr)
