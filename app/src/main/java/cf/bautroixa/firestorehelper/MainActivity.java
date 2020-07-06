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
import cf.bautroixa.firestorehelper.model.User;
import cf.bautroixa.firestoreodm.CollectionManager;
import cf.bautroixa.firestoreodm.DocumentsManager;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ModelManager modelManager = ModelManager.getInstance();
        final CollectionManager<User> baseUserManager = modelManager.getUserCollectionManager();

        String documentId = "1mwLI6XNS3bvqyNEzi4X";
        // get a document in collection
        Task<User> getTask = baseUserManager.requestGet(documentId);
        // get value from task
        getTask.addOnCompleteListener(this, new OnCompleteListener<User>() {
            @Override
            public void onComplete(@NonNull Task<User> task) {
                if (task.isSuccessful() && task.getResult() != null){
                    // 1. get user
                    User user = task.getResult();
                    // 2. get a friend
                    String friendDocumentId = "6vpnYJvoW23vkbREdUmU";
                    // 2.1 init sub-manager so that you can access it, choose one bellow
                    user.initSubManagerWithBaseManager(baseUserManager);
                    user.initSubManagerWithCollectionReference(baseUserManager.getRef());
                    // 2.2 get friend via sub-manager requestGet method
                    user.getFriendsManager().requestGet(friendDocumentId);
                }
            }
        });
        // query some document
        baseUserManager.queryGet(new DocumentsManager.QueryCreator() {
            @Override
            public Query create(CollectionReference collectionReference) {
                return collectionReference.whereEqualTo(User.NAME, "<your_name>");
            }
        });
        // wait for a document in collection
        DocumentsManager.OnDocumentGotListener<User> onGot = new DocumentsManager.OnDocumentGotListener<User>() {
            @Override
            public void onGot(User user) {
            }
        };
        baseUserManager.listenGet(this, documentId, onGot);
        baseUserManager.oneTimeListenGet(documentId, onGot);
        // get all document in collection
        baseUserManager.addOneTimeInitCompleteListener(new DocumentsManager.OnInitCompleteListener<User>() {
            @Override
            public void onComplete(ArrayList<User> list) {
            }
        });
        // listen for changes
        baseUserManager.attachListener(this, new DocumentsManager.OnListChangedListener<User>(){
            // overwrite method here
        });
        // show list to a recycler view adapter
        RecyclerView.Adapter adapter = null;
        baseUserManager.attachAdapter(this, adapter);
        // show list to a sorted list recyclerview
        SortedList<User> sortedList = null;
        baseUserManager.attachSortedList(this, sortedList);

    }
}
