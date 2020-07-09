package cf.bautroixa.firestoreodm;

import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;

public class CollectionManager<T extends Document> extends DocumentsManager<T> {
    protected String TAG = "CollectionManager";
    private Query query;
    private ListenerRegistration listenerRegistration;
    private boolean isListComplete = false;

    public CollectionManager(Class<T> itemClass) {
        super(itemClass);
        TAG = itemClass.getSimpleName() + TAG;
        onInitCompleteListeners = new ArrayList<>();
    }

    public CollectionManager(Class<T> itemClass, CollectionReference collectionReference) {
        super(itemClass, collectionReference);
        TAG = itemClass.getSimpleName() + TAG;
        onInitCompleteListeners = new ArrayList<>();
        this.setCollectionListener(collectionReference, false);
    }

    public CollectionManager(Class<T> itemClass, CollectionReference collectionReference, Document parentDocument) {
        super(itemClass, collectionReference, parentDocument);
        TAG = itemClass.getSimpleName() + TAG;
        onInitCompleteListeners = new ArrayList<>();
        this.setCollectionListener(collectionReference, false);
    }

    public CollectionManager(Class<T> itemClass, CollectionReference collectionReference, Query query) {
        super(itemClass, collectionReference);
        TAG = itemClass.getSimpleName() + TAG;
        onInitCompleteListeners = new ArrayList<>();
        this.query = query;
        this.setCollectionListener(query, false);
    }

    public CollectionManager(Class<T> itemClass, CollectionReference collectionReference, Query query, boolean autoRetry) {
        super(itemClass, collectionReference);
        TAG = itemClass.getSimpleName() + TAG;
        onInitCompleteListeners = new ArrayList<>();
        this.query = query;
        this.setCollectionListener(query, autoRetry);
    }

    public void startListening(@NonNull CollectionReference collectionReference){
        startListening(collectionReference, null);
    }

    public void startListening(@NonNull CollectionReference collectionReference, @Nullable Query query){
        if (isListening) throw new RuntimeException("You can not startListening when already listening to another one");
        this.ref = collectionReference;
        this.query = query;
        if (query != null) {
            setCollectionListener(query, true);
        } else {
            setCollectionListener(collectionReference, true);
        }
    }

    private void setCollectionListener(Query query, boolean autoRetry) {
        setCollectionListener(query, autoRetry ? 1000 : 0);
    }

    private void setCollectionListener(final Query query, final int retryInterval) {
        if (listenerRegistration != null) listenerRegistration.remove();
        isListening = true;
        listenerRegistration = query.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    Log.e(TAG, String.format("[Retry in %d ms] Listen %s failed reason: %s", retryInterval, ref.getId(), e.getMessage()));
                    isListening = false;
                    if (retryInterval > 0 && retryInterval < 10000) {
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                setCollectionListener(query, retryInterval * 2);
                            }
                        }, retryInterval);
                    } else {
                        Log.e(TAG, String.format("[TIMEOUT] Listen %s failed reason: %s", ref.getId(), e.getMessage()));
                    }
                    return;
                }
                if (queryDocumentSnapshots != null) {
                    for (DocumentChange documentChange : queryDocumentSnapshots.getDocumentChanges()) {
                        DocumentSnapshot documentSnapshot = documentChange.getDocument();
                        if (documentChange.getType() != DocumentChange.Type.REMOVED) {
                            T data = T.newInstance(itemClass, documentSnapshot);
                            data.setListening(true);
                            put(data);
                        } else {
                            remove(documentSnapshot.getId());
                        }
                    }
                }
                if (!isListComplete) {
                    isListComplete = true;
                    onListChanged();
                }
            }
        });
    }

    @Override
    public void onClear() {
        super.onClear();
        if (listenerRegistration != null) listenerRegistration.remove();
        isListening = false;
    }

    @Override
    public boolean isListComplete() {
        return isListComplete;
    }
}
