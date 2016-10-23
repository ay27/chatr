package ay27;

import ay27.encryptor.RSACoder;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.function.Predicate;

/**
 * @author ay27
 */
public class NetCore {

    private String mac, ip, name;
    private int port;
    private static final String STOP = "\0";
    private DatagramSocket udpChannel;
    private DatagramSocket fileChanel;
    private MulticastSocket mcastChannel;
    private MulticastSocket mcastSendChannel;
    private ArrayList<User> userList;

    private Thread mcastThread, udpThread;
    private boolean running = true;

    private String publicKey, privateKey;

    private NetCallback handler;

    //    private static final InetSocketAddress MCAST_ADDR = new InetSocketAddress("224.168.2.9", 12345);
    private static final String MCAST_ADDR = "228.0.0.4";
    private static final int MCAST_PORT = 8000;
    private static final String LOGIN = "login";
    private static final String LOGOUT = "logout";
    private static final String USERLIST = "userlist";
    private static final String MSG = "msg";
    private static final String FILE = "file";
    private static final String FILE_ACK = "file_ack";


    public interface NetCallback {
        void onUserlistUpdated(ArrayList<User> users);

        void onRecvMsg(User srcUser, String msg);

        void onRecvFile(User srcUser, String uuid, String fileName);

        void onRecvFileFinished(String storeFileName);
    }

    public NetCore(String name, NetCallback handler) throws Exception {
        this.name = name;
        this.handler = handler;
        this.port = new Random(12345).nextInt(50000) + 1024;
        this.udpChannel = new DatagramSocket(port);
        this.fileChanel = new DatagramSocket(port + 1);
        this.mcastSendChannel = new MulticastSocket();
        this.mcastChannel = new MulticastSocket(MCAST_PORT);
        this.mcastChannel.joinGroup(InetAddress.getByName(MCAST_ADDR));
        this.mac = GetLocalMac();
        this.ip = GetLocalAddress();
        this.userList = new ArrayList<>();

        Map<String, Object> keyMap = RSACoder.initKey();
        publicKey = RSACoder.getPublicKey(keyMap);
        privateKey = RSACoder.getPrivateKey(keyMap);
//        System.out.println("public key : " + publicKey);
//        System.out.println("private key : " + privateKey);
    }

    public void start() {
        running = true;
        mcastThread = new Thread(generateRunnable(mcastChannel));
        udpThread = new Thread(generateRunnable(udpChannel));
        mcastThread.start();
        udpThread.start();
    }

    private Runnable generateRunnable(DatagramSocket channel) {
        return () -> {
            byte[] buf = new byte[1024];
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            String data;
            while (running) {
                for (int i = 0; i < buf.length; i++) {
                    buf[i] = 0;
                }
                try {
                    channel.receive(dp);
                    System.out.println("recv dp from " + dp.getAddress().toString());
                    if (channel == udpChannel) {
                        String publicKey = null;
                        for (User user : userList) {
                            if (user.ip.equals(dp.getAddress().toString().substring(1))) {
                                publicKey = user.publicKey;
                                break;
                            }
                        }
                        if (publicKey == null) {
                            continue;
                        }
                        int len = buf.length - 1;
                        while (dp.getData()[len] == 0) {
                            len--;
                        }
                        len += 1;
                        byte[] tmp = new byte[len];
                        System.arraycopy(dp.getData(), 0, tmp, 0, len);
                        data = new String(RSACoder.decryptByPublicKey(tmp, publicKey), "UTF-8");
                    } else {
                        data = new String(dp.getData(), "UTF-8");
                    }
//                    System.out.println("recv raw data: " + data.trim());
                    String[] ss = data.split(STOP);
                    parsePacket(ss);
                } catch (SocketException e) {
                    // do nothing
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    private void parsePacket(String[] datas) {
        if (datas.length < 3) {
            throw new IllegalArgumentException("datas length is not satisfied, while len(datas)=" + datas.length);
        }

        if (datas[1].equals(mac)) {
            return;
        }

        System.out.println(Arrays.toString(datas));

        User tmp = (User) filter(userList, (user) -> ((User) user).equal(null, datas[1], null, -1));
        switch (datas[0]) {
            case LOGIN:
                if (tmp != null) {
                    userList.remove(tmp);
                }
                // is myself
                if (datas[1].equals(this.mac)) {
                    break;
                }
                userList.add(new User(datas[4], datas[1], datas[2], new Integer(datas[3]), datas[5]));
                handler.onUserlistUpdated(userList);
                break;
            case LOGOUT:
                if (tmp != null) {
                    userList.remove(tmp);
                    handler.onUserlistUpdated(userList);
                }
                break;
            case USERLIST:
                if (tmp != null) {
                    this.login(this.name);
                }
                break;
            case MSG:
                if (tmp != null) {
                    this.handler.onRecvMsg(tmp, datas[2]);
                }
                break;
            case FILE:
                if (tmp != null) {
                    handler.onRecvFile(tmp, datas[2], datas[3]);
                }
                break;
        }
    }

    private Object filter(List iters, Predicate cond) {
        if (iters == null || iters.size() < 1) {
            return null;
        }
        for (Object obj : iters) {
            if (cond.test(obj)) {
                return obj;
            }
        }
        return null;
    }

    public void stop() {
        running = false;
        try {
            mcastChannel.close();
            udpChannel.close();
            mcastThread.join();
            udpThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void login(String name) {
        this.name = name;
        mcast(msgLogin());
    }

    public void logout() {
        mcast(msgLogout());
    }

    public void updateUserList() {
        mcast(msgUserlist());
    }

    public void sendMsg(String msg, User targetUser) {
        send(msgSend(msg), new InetSocketAddress(targetUser.ip, targetUser.port));
    }

    public void updateMyName(String name) {
        this.name = name;
        logout();
        login(name);
    }

    private void mcast(String data) {
        _send(data, mcastSendChannel, new InetSocketAddress(MCAST_ADDR, MCAST_PORT));
    }

    private void send(String data, SocketAddress target) {
        _send(data, udpChannel, target);
    }

    public void recvFile(User srcUser, String uuid, File storeFile) {
        System.out.println("recv file from " + srcUser.name);
        new Thread(() -> {

            try {
                byte[] tmp = (FILE_ACK + STOP + uuid).getBytes();
                fileChanel.send(new DatagramPacket(tmp, tmp.length, new InetSocketAddress(srcUser.ip, srcUser.port + 1)));
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            byte[] buf = new byte[1024];
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(storeFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return;
            }

            System.out.println("recving");

            int cnt = 0;
            while (true) {
                try {
                    Arrays.fill(buf, (byte) 0);
                    System.out.println("cnt = " + cnt); cnt++;
                    fileChanel.receive(dp);
                    String dat = new String(dp.getData()).trim();
                    System.out.println(dat);
                    if (dat.equals(uuid)) {
                        break;
                    }
                    // check if end of file
                    fos.write(new String(dp.getData()).trim().getBytes());
                    fos.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            System.out.printf("finish");
            try {
                fos.close();
                handler.onRecvFileFinished(storeFile.getName());
            } catch (IOException e) {
                e.printStackTrace();
            }

        }).start();
    }

    public void sendFile(File file, User targetUser) {
        System.out.println("send file to " + targetUser.name);

        UUID uuid = UUID.randomUUID();
        _send(msgSendFileReq(uuid.toString(), file.getName()), udpChannel, new InetSocketAddress(targetUser.ip, targetUser.port));

        new Thread(() -> {
            byte[] buf = new byte[1024];
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            try {
                fileChanel.receive(dp);
                String data = new String(dp.getData(), "UTF-8");
                if (!data.split(STOP)[0].equals(FILE_ACK) || !data.split(STOP)[1].equals(uuid.toString())) {
                    System.out.println("file not act");
                    return;
                }
                System.out.println("start sending");
                int c;
                FileInputStream fis = new FileInputStream(file);
                InetSocketAddress target = new InetSocketAddress(targetUser.ip, targetUser.port + 1);
                while ((c = fis.read(buf)) != -1) {
                    dp = new DatagramPacket(buf, c, target);
                    System.out.println("c = " + c);
                    fileChanel.send(dp);
                    Thread.sleep(1000);
                }
                System.out.println("send finish");
                byte[] tmp = uuid.toString().getBytes();
                fileChanel.send(new DatagramPacket(tmp, tmp.length, target));
                fis.close();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void _send(String data, DatagramSocket channel, SocketAddress target) {
        System.out.println("send " + data + " using " + channel + " target: " + target);
        try {
            byte[] bytes = data.getBytes("UTF-8");
            if (channel == udpChannel) {
                // encrypt data
                bytes = RSACoder.encryptByPrivateKey(bytes, privateKey);
            }
            DatagramPacket dp = new DatagramPacket(bytes, bytes.length, target);
            channel.send(dp);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String msgLogin() {
        return LOGIN + STOP + mac + STOP + ip + STOP + port + STOP + name + STOP + publicKey;
    }

    private String msgLogout() {
        return LOGOUT + STOP + mac + STOP + ip + STOP + port + STOP + name;
    }

    private String msgUserlist() {
        return USERLIST + STOP + mac + STOP + ip + STOP + port + STOP + name;
    }

    private String msgSendFileReq(String uuid, String fileName) {
        return FILE + STOP + mac + STOP + uuid + STOP + fileName;
    }

//    private String msgSendFileEnd(String uuid) {
//        return FILE + STOP + mac + STOP + uuid;
//    }

    private String msgSend(String content) {
        return MSG + STOP + mac + STOP + content;
//        return MSG + STOP + mac + STOP + ip + STOP + port + STOP + name + STOP + content;
    }

    private static String GetLocalAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String GetLocalMac() throws SocketException, UnknownHostException {
        byte[] mac = NetworkInterface.getByInetAddress(InetAddress.getLocalHost()).getHardwareAddress();
        StringBuilder sb = new StringBuilder("");
        for (int i = 0; i < mac.length; i++) {
            if (i != 0) {
                sb.append("-");
            }
            //字节转换为整数
            int temp = mac[i] & 0xff;
            String str = Integer.toHexString(temp);
            if (str.length() == 1) {
                sb.append("0").append(str);
            } else {
                sb.append(str);
            }
        }
        return sb.toString().toUpperCase();
    }
}
