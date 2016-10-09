# Created by ay27 at 16/10/4

from chatr_core.api import *


class CmdMsgHanler(MsgHandler):
    def recv_msg(self, user, msg):
        print('recv from : ', user)
        print(msg)

    def update_user_list(self, user_list):
        print('update user list')
        for u in user_list:
            print(u)


if __name__ == '__main__':
    chatr = API(CmdMsgHanler())
    chatr.login('fuck')
    chatr.update_user_list()
    user_list = chatr.user_list
    while True:
        msg = input()
        if len(user_list) > 0:
            print('send msg to %s : %s' % (user_list[0].name, msg))
            chatr.send_msg(msg, user_list[0])
    chatr.logout()
