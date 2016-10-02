# Created by ay27 at 16/10/1
from chatr_core.networking import *
import uuid

mcast_addr = '230.230.1.1'
mport = 12345
udp_port = 12346

# login(logout) mac ip port name
LOGIN_MSG = 'login\0%s\0%s\0%d\0%s'
LOGOUT_MSG = 'login\0%s\0%s\0%d\0%s'

# userlist mac ip port name
USERLIST = 'userlist\0%s\0%s\0%d\0%s'


def get_local_addr():
    mac = uuid.UUID(int=uuid.getnode()).hex[-12:]
    mac = ":".join([mac[e:e + 2] for e in range(0, 11, 2)])
    myname = socket.getfqdn(socket.gethostname())
    myaddr = socket.gethostbyname(myname)
    return mac, myaddr


class API:
    def __init__(self, user_list_updater, msg_receiver, file_receiver=None):
        self.user_list_updater = user_list_updater
        self.msg_receiver = msg_receiver
        self.file_receiver = file_receiver
        self.mcast_channel = Multicast(mcast_addr, mport, self.mcast_handler)
        self.udp_channel = DataGram(udp_port, self.udp_handler)
        self.mac, self.myaddr = get_local_addr()
        self.name = ''
        self.user_list = []
        self.msg_list = dict()

    def mcast_handler(self, data, addr):
        pass

    def udp_handler(self, data, addr):
        if addr not in [(row[1], row[2]) for row in self.user_list]:
            return
        if self.msg_list.get(addr) is None:
            self.msg_list[addr] = []
        self.msg_list[addr].append(data)
        self.msg_receiver(data,)

    def login(self, name):
        # TODO unique name
        if not isinstance(name, str) or len(name) < 1:
            raise ValueError('name must be a string and length >= 1')
        self.name = name + self.mac
        self.mcast_channel.send(LOGIN_MSG % (self.mac, self.myaddr, udp_port, name))

    def logout(self):
        self.mcast_channel.send(LOGOUT_MSG % (self.mac, self.myaddr, udp_port, self.name))

    def update_user_list(self):
        self.mcast_channel.send(USERLIST % (self.mac, self.myaddr, udp_port, self.name))

    def send_msg(self, msg, target_user):
        self.udp_channel.send(msg, target_user)
