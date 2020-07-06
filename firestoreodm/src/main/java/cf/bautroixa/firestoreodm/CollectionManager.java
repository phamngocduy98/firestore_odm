package cf.bautroixa.firestoreodm;

import android.os.Handler;
import android.util.Log;

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
    //    protected Query query;
    private ListenerRegistration listenerRegistration;
    private ArrayList<OnInitCompleteListener<T>> onInitCompleteListeners;
    private boolean autoRetry = true;
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
        this.setCollectionListener(collectionReference, autoRetry);
    }

    public CollectionManager(Class<T> itemClass, CollectionReference collectionReference, Query query) {
        super(itemClass, collectionReference);
        TAG = itemClass.getSimpleName() + TAG;
        onInitCompleteListeners = new ArrayList<>();
        this.query = query;
        this.setCollectionListener(query, autoRetry);
    }

    public CollectionManager(Class<T> itemClass, CollectionReference collectionReference, Query query, boolean autoRetry) {
        super(itemClass, collectionReference);
        TAG = itemClass.getSimpleName() + TAG;
        onInitCompleteListeners = new ArrayList<>();
        this.query = query;
        this.autoRetry = autoRetry;
        this.setCollectionListener(query, autoRetry);
    }

    private void setCollectionListener(Query query, boolean autoRetry) {
        setCollectionListener(query, autoRetry, 1000);
    }

    private void setCollectionListener(final Query query, final boolean autoRetry, final int retryInterval) {
        if (listenerRegistration != null) listenerRegistration.remove();
        listenerRegistration = query.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    Log.e(TAG, String.format("[Retry in %d ms] Listen %s failed reason: %s", retryInterval, ref.getId(), e.getMessage()));
                    setListening(false);
                    if (autoRetry && retryInterval < 60 * 1000) {
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                setCollectionListener(query, autoRetry, retryInterval * 2);
                            }
                        }, retryInterval);
                    } else {
                        Log.e(TAG, String.format("[TIMEOUT] Listen %s failed reason: %s", ref.getId(), e.getMessage()));
                    }
                    return;
                }
                setListening(true);
                if (queryDocumentSnapshots != null) {
                    for (DocumentChange documentChange : queryDocumentSnapshots.getDocumentChanges()) {
                        DocumentSnapshot documentSnapshot = documentChange.getDocument();
                        if (documentChange.getType() != DocumentChange.Type.REMOVED) {
                            put(T.newInstance(itemClass, documentSnapshot));
                        } else {
                            remove(documentSnapshot.getId());
                        }
                    }
                }
                if (!isListComplete) {
                    isListComplete = true;
                    for (OnInitCompleteListener<T> onInitCompleteListener : onInitCompleteListeners) {
                        onInitCompleteListener.onComplete(list);
                        removeOnInitCompleteListener(onInitCompleteListener);
                    }
                }
            }
        });
    }

    @Override
    public void onClear() {
        super.onClear();
        if (listenerRegistration != null) listenerRegistration.remove();
    }

    public void removeOnInitCompleteListener(OnInitCompleteListener<T> onInitCompleteListener) {
        this.onInitCompleteListeners.remove(onInitCompleteListener);
    }

    public void addOneTimeInitCompleteListener(final OnInitCompleteListener<T> onInitCompleteListener) {
        if (isListComplete) {
            onInitCompleteListener.onComplete(list);
            return;
        }
        this.onInitCompleteListeners.add(new OnInitCompleteListener<T>() {
            @Override
            public void onComplete(ArrayList list) {
                onInitCompleteListener.onComplete(list);
                removeOnInitCompleteListener(onInitCompleteListener);
            }
        });
    }
}
