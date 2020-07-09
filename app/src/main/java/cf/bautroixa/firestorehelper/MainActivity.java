package cf.bautroixa.firestorehelper;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SortedList;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;

import cf.bautroixa.firestorehelper.model.ModelManager;
import cf.bautroixa.firestorehelper.model.Notification;
import cf.bautroixa.firestorehelper.model.User;
import cf.bautroixa.firestoreodm.CollectionManager;
import cf.bautroixa.firestoreodm.Document;
import cf.bautroixa.firestoreodm.DocumentsManager;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ModelManager modelManager = ModelManager.getInstance();
        final CollectionManager<User> baseUserManager = modelManager.getUserCollectionManager();

        String userDocumentId = "1mwLI6XNS3bvqyNEzi4X";

        // 0. GET AND LISTEN CHANGES OF SINGLE DOCUMENT:
        // 0.1 create Document instance
        User myUser = new User();
        // 0.2 add DocumentReference
        myUser.withRef(baseUserManager.getDocumentReference(userDocumentId));
        // 0.3 start getting and listening for value
        myUser.setListenerRegistration(1000, null);
        // 0.4 init subManager if it has
        myUser.initSubManager(baseUserManager);
        // 0.5 listen for changes
        myUser.attachListener(this, new Document.OnValueChangedListener<User>() {
            @Override
            public void onValueChanged(@NonNull User user) {
                if (user.isAvailable()) { //check if myUser contains Document data or just empty
                    // you can use "user" now
                }
            }
        });

        // 0.6 if you want to reuse myUser object and listen to another document
        String anotherUserDocumentId = "2wLI62h2d2vqyNEzi4X";
        myUser.onRemove();
        myUser.withRef(baseUserManager.getDocumentReference(anotherUserDocumentId));
        // retry interval = 0 to disable retry
        // you can specify initListener, callback when myUser receive data from document
        myUser.setListenerRegistration(0, new Document.OnValueChangedListener<User>() {
            @Override
            public void onValueChanged(@NonNull User user) {
                // callback when myUser receive data from document
            }
        });
        // you can reuse its subManager too:
        myUser.initSubManager(baseUserManager);


        // 1. GET A DOCUMENT FROM DOCUMENTS MANAGER
        String friendDocumentId = myUser.getFriends().get(0).getId();
        // 1.1 DocumentsManager automatically get all available document
        // so you can wait until the document is got then get it.
        Task<User> getAFriendTask1 = myUser.getFriendsManager().waitGet(friendDocumentId);
        // 1.2 if you want to get without wait, you can requestGet
        Task<User> getAFriendTask2 = myUser.getFriendsManager().requestGet(friendDocumentId);

        // then you can get value from task
        getAFriendTask2.addOnCompleteListener(this, new OnCompleteListener<User>() {
            @Override
            public void onComplete(@NonNull Task<User> task) {
                if (task.isSuccessful() && task.getResult() != null) {
                    User gotUser = task.getResult();
                }
            }
        });
        // 1.3 query for user whose name is "Pham Ngoc Duy"
        baseUserManager.queryGet(new DocumentsManager.QueryCreator() {
            @Override
            public Query create(CollectionReference collectionReference) {
                return collectionReference.whereEqualTo(User.NAME, "Pham Ngoc Duy");
            }
        });

        // 2. get all document in collection
        myUser.getFriendsManager().waitUntilInitComplete(new DocumentsManager.OnInitCompleteListener<User>() {
            @Override
            public void onComplete(ArrayList<User> list) {
            }
        });
        // 3.1 listen for changes
        myUser.getNotificationsManager().attachListener(this, new DocumentsManager.OnListChangedListener<Notification>() {
            @Override
            public void onItemInserted(int position, Notification data) {
            }

            @Override
            public void onItemChanged(int position, Notification data) {
            }

            @Override
            public void onItemRemoved(int position, Notification data) {
            }

            @Override
            public void onDataSetChanged(ArrayList<Notification> list) {
            }

            @Override
            public void onListSizeChanged(ArrayList<Notification> list, int size) {
            }
        });
        // 3.2 show list to a recycler view adapter
        RecyclerView.Adapter adapter = null;
        baseUserManager.attachAdapter(this, adapter);
        // 3.3 show list to a sorted list recyclerview
        SortedList<User> sortedList = null;
        baseUserManager.attachSortedList(this, sortedList);

    }
}
