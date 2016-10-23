package ay27;

import java.io.File;
import java.util.List;

/**
 * Created by ay27 on 16/10/15.
 */
public interface UiIntef {
    void setMsgViewerText(List<Message> msgs);

    String getMsgEditorText();

    void cleanMsgEditor();

    void setFriendList(String[] friends);

    int getSelectedUser();

    void selectUser(int index);

    void setMySelfName(String name);

    File recvFile(String userName, String fileName);


    public interface Callback {

        void onSendBtnAction(UiIntef self);

        void onFriendSelected(UiIntef self, int index);

        void onMySelfNameChanged(String name);

        void onWindowClosing();

        void onSendFile(File file);
    }
}
