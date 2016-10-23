/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ay27;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

/**
 * @author ay27
 */
public class Chatr {

    static class UserSession {
        User user;
        ArrayList<Message> msgs;

        public UserSession(User user) {
            this.user = user;
            this.msgs = new ArrayList<>();
        }

        public void sortMsgs() {
            msgs.sort((m1, m2) -> (int) (m1.getDate().getTime() - m2.getDate().getTime()));
        }
    }

    static void mergeList(ArrayList<UserSession> dst, ArrayList<User> src) {
        for (User us1 : src) {
            UserSession tmp = null;
            for (UserSession us2 : dst) {
                if (us1.mac.equals(us2.user.mac)) {
                    tmp = us2;
                    break;
                }
            }
            if (tmp == null) {
                dst.add(new UserSession(us1));
            } else {
                tmp.user = us1;
            }
        }
        for (UserSession us : dst) {
            us.sortMsgs();
        }
    }

    public static void main(String[] args) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(Ui.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(Ui.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(Ui.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(Ui.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new Chatr();
            }
        });
    }

    private UiIntef ui;
    private NetCore netCore;

    private String mySelfName;

    private ArrayList<UserSession> sessions = new ArrayList<>();

    UiIntef.Callback uiHandler = new UiIntef.Callback() {

        @Override
        public void onSendBtnAction(UiIntef self) {
            int userIndex = self.getSelectedUser();
            userIndex = userIndex < 0 ? 0 : userIndex;
            String msg = self.getMsgEditorText();

            if (msg.isEmpty()) {
                return;
            }

            netCore.sendMsg(msg, sessions.get(userIndex).user);
            sessions.get(userIndex).msgs.add(new Message(mySelfName, msg, true));
            self.setMsgViewerText(sessions.get(userIndex).msgs);
            self.cleanMsgEditor();
        }

        @Override
        public void onFriendSelected(UiIntef self, int index) {
            self.setMsgViewerText(sessions.get(index).msgs);
            self.cleanMsgEditor();
        }

        @Override
        public void onMySelfNameChanged(String name) {
            netCore.updateMyName(name);
        }

        @Override
        public void onWindowClosing() {
            netCore.logout();
            netCore.stop();
        }

        @Override
        public void onSendFile(File file) {
            netCore.sendFile(file, sessions.get(ui.getSelectedUser()).user);
        }
    };

    NetCore.NetCallback handler = new NetCore.NetCallback() {
        @Override
        public void onUserlistUpdated(ArrayList<User> users) {
            mergeList(sessions, users);
            String[] tmp = new String[users.size()];
            for (int i = 0; i < users.size(); i++) {
                tmp[i] = users.get(i).name;
            }
            ui.setFriendList(tmp);
            if ((ui.getSelectedUser() < 0) && (users.size() > 0)) {
                ui.selectUser(0);
            }
        }

        @Override
        public void onRecvMsg(User srcUser, String msg) {
            for (UserSession us : sessions) {
                if (us.user.mac.equals(srcUser.mac)) {
                    us.msgs.add(new Message(srcUser.name, msg, false));
                    if (ui.getSelectedUser() == sessions.indexOf(us)) {
                        ui.setMsgViewerText(us.msgs);
                    }
                    break;
                }
            }
        }

        @Override
        public void onRecvFile(User srcUser, String uuid, String fileName) {
            File storeFile = ui.recvFile(srcUser.name, fileName);
            if (storeFile == null) {
                return;
            }
            netCore.recvFile(srcUser, uuid, storeFile);
        }

        @Override
        public void onRecvFileFinished(String storeFileName) {
            JOptionPane.showMessageDialog(null, "文件 "+storeFileName + " 接收完毕!");
        }
    };

    public Chatr() {

        System.setProperty("java.net.preferIPv4Stack", "true");

        // 设置自己的名字
        String mySelfName = JOptionPane.showInputDialog("输入你的名字");
        mySelfName = ((mySelfName == null) || (mySelfName.equals(""))) ? UUID.randomUUID().toString() : mySelfName;

        ui = new Ui(uiHandler);
        ui.setMySelfName(mySelfName);

        try {
            netCore = new NetCore(mySelfName, handler);
            netCore.start();
            netCore.login(mySelfName);
            netCore.updateUserList();
        } catch (IOException e) {
            e.printStackTrace();
            netCore.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
