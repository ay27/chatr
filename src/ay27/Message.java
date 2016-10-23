package ay27;

import java.util.Date;

/**
 * Created by ay27 on 16/10/17.
 */
public class Message {
    private String userName;
    private boolean isMySelf;
    private String content;
    private Date date;

    public Message(String userName, String content, boolean isMySelf) {
        this.userName = userName;
        this.content = content;
        this.isMySelf = isMySelf;
        this.date = new Date();
    }

    public String getUserName() {
        return userName;
    }

    public boolean isMySelf() {
        return isMySelf;
    }

    public String getContent() {
        return content;
    }

    public Date getDate() {
        return date;
    }
}
