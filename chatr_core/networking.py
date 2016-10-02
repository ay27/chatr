# Created by ay27 at 16/10/2
import socket
import threading

import select


class Multicast:
    def __init__(self, mcast_addr, port, recv_handler):
        self.mcast_addr = mcast_addr
        self.port = port
        self.recv_handler = recv_handler
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        self.sock.bind(('', port))
        self.sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 255)  # 设置使用多播发送

        self.RUNNING = True
        self.recv_thread = threading.Thread(target=self.recving)
        self.recv_thread.start()

    def recving(self):
        self.sock.setblocking(0)
        while self.RUNNING:
            ready = select.select([self.sock], [], [], 10)
            if ready[0]:
                data, addr = self.sock.recvfrom(1024)
                data = data.decode(encoding='utf-8')
                self.recv_handler(data, addr)

    def send(self, msg):
        data = None
        if isinstance(msg, str):
            data = msg.encode(encoding='utf-8')
        elif isinstance(msg, bytes):
            data = msg
        else:
            raise ValueError('msg must be a string or a bytes')
        self.sock.sendto(data, (self.mcast_addr, self.port))

    def stop(self):
        self.RUNNING = False


class DataGram:
    def __init__(self, port, recv_handler):
        self.port = port
        self.recv_handler = recv_handler
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.bind(('', port))
        self.RUNNING = True
        self.recv_thread = threading.Thread(self.recving)
        self.recv_thread.start()

    def recving(self):
        self.sock.setblocking(0)
        while self.RUNNING:
            ready = select.select([self.sock], [], [], 10)
            if ready[0]:
                data, addr = self.sock.recvfrom(1024)
                data = data.decode(encoding='utf-8')
                self.recv_handler(data, addr)

    def send(self, msg, target):
        data = None
        if isinstance(msg, str):
            data = msg.encode(encoding='utf-8')
        elif isinstance(msg, bytes):
            data = msg
        else:
            raise ValueError('msg must be a string or a bytes')
        self.sock.sendto(data, target)

    def stop(self):
        self.RUNNING = False
