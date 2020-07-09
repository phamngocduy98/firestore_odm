package cf.bautroixa.firestorehelper.model;

import com.google.firebase.firestore.DocumentReference;

import java.util.List;

import cf.bautroixa.firestoreodm.CollectionManager;
import cf.bautroixa.firestoreodm.Document;
import cf.bautroixa.firestoreodm.DocumentsManager;
import cf.bautroixa.firestoreodm.RefsArrayManager;

/**
 * You have a user collection in the Firestore database
 * a User document contains {
 * name: String
 * friends: Array<DocumentReference>
 * }
 * a User document also contains a sub-collection Notification
 */
public class User extends Document {
    // PROPERTY NAME FOR UPDATING VALUE
    public static final String NAME = "name";
    public static final String FRIENDS = "friends";

    // Document properties
    private String name;
    private List<DocumentReference> friends;

    // sub-manager helps manage {@link User#friends} property
    private RefsArrayManager<User> friendsManager;
    // sub-manager helps manage sub-collection "notifications"
    private CollectionManager<Notification> notificationsManager;

    // implement update method for updating new value
    @Override
    protected void update(Document document) {
        User user = (User) document;
        name = user.getName();
        // use customized setFriends to update friendsManager
        setFriends(user.getFriends());

        // update the friendsManager
        // isSubManagerAvailable() show that subManager is initiated or not
        if (friends != null && isSubManagerAvailable()) {
            friendsManager.updateRefList(friends);
        }
    }

    // implement your own sub-manager initiation method you want
    public void initSubManager(DocumentsManager<User> baseUserManager) {
        if (!isSubManagerAvailable()) { // you need a check before init
            friendsManager = new RefsArrayManager<>(User.class, baseUserManager);
            notificationsManager = new CollectionManager<>(Notification.class, ref.collection("notifications"));
            // remember to setSubManagerAvailable to true so that it can be updated
            setSubManagerAvailable(true);
        }
        // when reuse User object, you can reuse it's sub-manager too:
        if (!notificationsManager.isListening()) {
            notificationsManager.startListening(ref.collection("notifications"));
        }
    }

    // overwrite onRemove to clear subManager item
    @Override
    public void onRemove() {
        super.onRemove();
        if (isSubManagerAvailable()) { // you need a check
            friendsManager.clear();
        }
    }

    // getter for friendsManager
    public RefsArrayManager<User> getFriendsManager() {
        return friendsManager;
    }

    // getter for notificationsManager
    public CollectionManager<Notification> getNotificationsManager() {
        return notificationsManager;
    }

    // normal getter and setter as you want
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<DocumentReference> getFriends() {
        return friends;
    }

    public void setFriends(List<DocumentReference> friends) {
        this.friends = friends;
        if (isSubManagerAvailable() && friends != null) friendsManager.updateRefList(friends);
    }
}
