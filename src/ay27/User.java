package ay27;

/**
 * Created by ay27 on 16/10/15.
 */
public class User {
    public String name;
    public String ip, mac;
    public int port;
    public String publicKey;

    public User(String name, String mac, String ip, int port, String publicKey) {
        this.name = name;
        this.mac = mac;
        this.ip = ip;
        this.port = port;
        this.publicKey = publicKey;
    }

    public boolean equal(String name, String mac, String ip, int port) {
        return (name == null || this.name.equals(name)) && (mac == null || this.mac.equals(mac))
                && (ip == null || this.ip.equals(ip)) && ((port == -1) || this.port == port);
    }
}
