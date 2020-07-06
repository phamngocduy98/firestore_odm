package cf.bautroixa.firestorehelper.model;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;

import java.util.List;

import cf.bautroixa.firestoreodm.Document;
import cf.bautroixa.firestoreodm.DocumentsManager;
import cf.bautroixa.firestoreodm.RefsArrayManager;

public class User extends Document {
    // PROPERTY NAME FOR UPDATING VALUE
    public static final String NAME = "name";
    public static final String FRIENDS = "friends";

    // Document properties
    private String name;
    private List<DocumentReference> friends;

    // sub-manager helps manage "friends" property
    private RefsArrayManager<User> friendsManager;

    // implement update method for updating new value
    @Override
    protected void update(Document document) {
        User user = (User) document;
        name = user.getName();
        friends = user.getFriends();

        // update the friendsManager
        // isSubManagerAvailable() show that subManager is initiated or not
        if (friends != null && isSubManagerAvailable()){
            friendsManager.updateRefList(friends);
        }
    }

    // implement your own sub-manager initiation method you want
    public void initSubManagerWithCollectionReference(CollectionReference userCollectionRef){
        friendsManager = new RefsArrayManager<>(User.class, userCollectionRef);
        // remember to setSubManagerAvailable to true so that it can be updated
        setSubManagerAvailable(true);
    }

    public void initSubManagerWithBaseManager(DocumentsManager<User> baseUserManager){
        friendsManager = new RefsArrayManager<>(User.class, baseUserManager);
        // remember to setSubManagerAvailable to true so that it can be updated
        setSubManagerAvailable(true);
    }

    // getter for friendsManager
    public RefsArrayManager<User> getFriendsManager() {
        return friendsManager;
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
    }
}
