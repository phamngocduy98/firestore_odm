package cf.bautroixa.firestoreodm;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;

import java.util.ArrayList;
import java.util.List;


public class RefsArrayManager<T extends Document> extends DocumentsManager<T> {
    private String TAG = "ArrayManager";
    private DocumentsManager<T> parentDocumentsManager;
    private int requiredListSize = 0;
    private ArrayList<OnInitCompleteListener<T>> onInitCompleteListeners;

    public RefsArrayManager(Class<T> itemClass) {
        super(itemClass);
        TAG = itemClass.getSimpleName() + TAG;
        this.onInitCompleteListeners = new ArrayList<>();
    }

    public RefsArrayManager(Class<T> itemClass, @NonNull DocumentsManager<T> parentDocumentsManager) {
        super(itemClass, parentDocumentsManager.ref);
        TAG = "Child" + itemClass.getSimpleName() + TAG;
        this.parentDocumentsManager = parentDocumentsManager;
        this.onInitCompleteListeners = new ArrayList<>();
    }

    public RefsArrayManager(Class<T> itemClass, @NonNull CollectionReference collectionReference) {
        super(itemClass, collectionReference);
        TAG = itemClass.getSimpleName() + TAG;
        this.onInitCompleteListeners = new ArrayList<>();
    }

    @Override
    public void put(T data) {
        super.put(data);
//        if (parentDocumentsManager != null) parentDocumentsManager.put(data);
        onListUpdated();
    }

    @Override
    public void remove(String id) {
        super.remove(id);
        onListUpdated();
    }

    public void updateRefList(List<DocumentReference> documentReferences) {
        requiredListSize = documentReferences.size();
        // clean up removed item
        for (int i = 0; i < list.size(); i++) {
            T data = list.get(i);
            if (!documentReferences.contains(data.getRef())) {
                Log.d(TAG, "delete" + data.getId());
                remove(data.getId());
                i--;
            }
        }
        // add or update
        for (final DocumentReference ref : documentReferences) {
            if (parentDocumentsManager == null) {
                final Integer index = mapIdWithIndex.get(ref.getId());
                if (index == null) {
                    // add
                    Log.d(TAG, "listen New Document " + ref.getId());
                    listenNewDocument(ref);
                }
            } else {
                Log.d(TAG, "get from parent " + ref.getId());
                parentDocumentsManager.requestListen(ref.getId(), new Document.OnValueChangedListener<T>() {
                    @Override
                    public void onValueChanged(@NonNull T data) {
                        put(data);
                    }
                });
            }
        }
    }


    public void removeOnInitCompleteListener(OnInitCompleteListener<T> onInitCompleteListener) {
        this.onInitCompleteListeners.remove(onInitCompleteListener);
    }

    public void addOneTimeInitCompleteListener(final OnInitCompleteListener<T> onInitCompleteListener) {
        this.onInitCompleteListeners.add(new OnInitCompleteListener<T>() {
            @Override
            public void onComplete(ArrayList list) {
                onInitCompleteListener.onComplete(list);
                removeOnInitCompleteListener(onInitCompleteListener);
            }
        });
        if (isListComplete()) {
            onInitCompleteListener.onComplete(list);
            removeOnInitCompleteListener(onInitCompleteListener);
        }
    }

    public boolean isListComplete() {
        return requiredListSize == list.size();
    }

    private void onListUpdated() {
        if (isListComplete()) {
            for (OnInitCompleteListener<T> onInitCompleteListener : onInitCompleteListeners) {
                onInitCompleteListener.onComplete(list);
            }
        }
    }
}
