package cf.bautroixa.firestoreodm;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;

import java.util.List;


public class RefsArrayManager<T extends Document> extends DocumentsManager<T> {
    private String TAG = "ArrayManager";
    private DocumentsManager<T> parentDocumentsManager;
    private int requiredListSize = 0;

    public RefsArrayManager(Class<T> itemClass) {
        super(itemClass);
        TAG = itemClass.getSimpleName() + TAG;
    }

    public RefsArrayManager(Class<T> itemClass, @NonNull DocumentsManager<T> parentDocumentsManager) {
        super(itemClass, parentDocumentsManager.getRef());
        this.parentDocumentsManager = parentDocumentsManager;
        TAG = "Child" + itemClass.getSimpleName() + TAG;
    }

    public RefsArrayManager(Class<T> itemClass, @NonNull CollectionReference collectionReference) {
        super(itemClass, collectionReference);
        TAG = itemClass.getSimpleName() + TAG;
    }

    public RefsArrayManager(Class<T> itemClass, CollectionReference collectionReference, Document parentDocument) {
        super(itemClass, collectionReference, parentDocument);
        TAG = itemClass.getSimpleName() + TAG;
    }

    public void updateRefList(List<DocumentReference> documentReferences) {
        requiredListSize = documentReferences.size();
        // clean up removed item
        for (int i = 0; i < list.size(); i++) {
            T document = list.get(i);
            if (!documentReferences.contains(document.getRef())) {
                Log.d(TAG, "delete" + document.getId());
                remove(document.getId());
                i--;
            }
        }
        // add or update
        for (final DocumentReference ref : documentReferences) {
            if (parentDocumentsManager == null) {
                final Integer index = mapIdWithIndex.get(ref.getId());
                if (index == null) { // add
                    Log.d(TAG, "listen New Document " + ref.getId());
                    listenNewDocument(ref);
                }
            } else {
                Log.d(TAG, "get from parent " + ref.getId());
                T data = parentDocumentsManager.getFromParent(ref);
                data.addDocumentsManager(this);
            }
        }
    }

    @Override
    public T remove(String id) {
        T data = super.remove(id);
        if (parentDocumentsManager == null && data != null) data.onRemove();
        return data;
    }

    @Override
    public void onClear() {
        if (parentDocumentsManager == null) {
            for (Document document : list) {
                // remove listener and relate property (like latLng, marker) of each data
                document.onRemove();
            }
        }
    }

    @Override
    public boolean isListComplete() {
        return requiredListSize == list.size();
    }
}
