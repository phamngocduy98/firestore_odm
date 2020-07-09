package cf.bautroixa.firestoreodm;

import android.os.Handler;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SortedList;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * DocumentsManager class
 *
 * @param <T> extends Document, Document type
 * @author PhamNgocDuy
 * @version 0.0.1
 * @since 2020/07/07
 */
public abstract class DocumentsManager<T extends Document> {
    protected Document parentDocument;
    protected CollectionReference ref;
    protected Class<T> itemClass;
    protected HashMap<String, Integer> mapIdWithIndex;
    protected ArrayList<T> list;
    protected ArrayList<OnListChangedListener<T>> onListChangedListeners;
    protected ArrayList<OnInitCompleteListener<T>> onInitCompleteListeners;
    protected boolean isListening = true;
    protected boolean isListComplete = false;
    private String TAG = "Manager";

    /**
     * construct new empty,boring,useless DocumentsManager
     *
     * @param itemClass Document class of its item
     */
    public DocumentsManager(Class<T> itemClass) {
        this.itemClass = itemClass;
        constructor(itemClass);
    }

    /**
     * construct new DocumentsManager
     *
     * @param itemClass           Document class of its item
     * @param collectionReference CollectionReference it manages
     */
    public DocumentsManager(Class<T> itemClass, CollectionReference collectionReference) {
        this.ref = collectionReference;
        constructor(itemClass);
    }

    /**
     * construct new Document with parentDocument
     *
     * @param itemClass           Document class of its item
     * @param collectionReference CollectionReference it manages
     * @param parentDocument      its parent
     */
    public DocumentsManager(Class<T> itemClass, CollectionReference collectionReference, Document parentDocument) {
        this.ref = collectionReference;
        this.parentDocument = parentDocument;
        constructor(itemClass);
    }

    private void constructor(Class<T> itemClass) {
        this.itemClass = itemClass;
        TAG = itemClass.getSimpleName() + TAG;
        this.mapIdWithIndex = new HashMap<>();
        this.list = new ArrayList<>();
        this.onListChangedListeners = new ArrayList<>();
        this.onInitCompleteListeners = new ArrayList<>();
    }

    public CollectionReference getRef() {
        return ref;
    }

    public boolean isListening() {
        return isListening;
    }

    public void setParentDocument(Document parentDocument) {
        this.parentDocument = parentDocument;
    }

    public Document getParentDocument() {
        return parentDocument;
    }

    /**
     * get DocumentReference to specified document id
     *
     * @param documentId
     * @return DocumentReference
     */
    public DocumentReference getDocumentReference(String documentId) {
        if (documentId == null) return ref.document();
        return ref.document(documentId);
    }

    /**
     * generate new DocumentReference
     *
     * @return DocumentReference
     */
    public DocumentReference getNewDocumentReference() {
        return ref.document();
    }

    /**
     * create a new document in collection with WriteBatch
     *
     * @param batch create document in this WriteBatch
     * @param data  Document to create, use {@link Document#withRef(DocumentReference)}
     *              to specify a custom DocumentReference or documentId
     * @return DocumentReference to newly created document
     */
    public DocumentReference create(WriteBatch batch, T data) {
        DocumentReference newDataRef = data.getId() != null ? ref.document(data.getId()) : ref.document();
        batch.set(newDataRef, data);
        return newDataRef;
    }

    /**
     * create a new document in collection without WriteBatch
     *
     * @param data Document to create, use {@link Document#withRef(DocumentReference)}
     *             to specify a custom DocumentReference or documentId
     * @return Task of DocumentReference which DocumentReference reference to newly created document
     */
    public Task<DocumentReference> create(T data) {
        final DocumentReference newDataRef = data.getId() != null ? ref.document(data.getId()) : ref.document();
        return newDataRef.set(data).continueWith(new Continuation<Void, DocumentReference>() {
            @Override
            public DocumentReference then(@NonNull Task<Void> task) throws Exception {
                if (!task.isSuccessful()) throw task.getException();
                return newDataRef;
            }
        });
    }

    /**
     * delete a new document in collection with WriteBatch
     *
     * @param batch      delete document in this WriteBatch
     * @param documentId documentId
     */
    public void delete(@NonNull WriteBatch batch, String documentId) {
        DocumentReference dataRef = ref.document(documentId);
        batch.delete(dataRef);
    }

    /**
     * delete a new document in collection without WriteBatch
     *
     * @param documentId
     * @return Task of deletion action
     */
    public Task<Void> delete(String documentId) {
        DocumentReference dataRef = ref.document(documentId);
        return dataRef.delete();
    }

    /**
     * requestGet get a document with documentId
     *
     * @param documentId documentId to get
     * @return Task contains Document value
     */
    public Task<T> requestGet(String documentId) {
        T data = get(documentId);
        if (data != null) {
            return TaskHelper.getCompletedTask(data);
        }
        return ref.document(documentId).get().continueWith(new Continuation<DocumentSnapshot, T>() {
            @Override
            public T then(@NonNull Task<DocumentSnapshot> task) throws Exception {
                if (!task.isSuccessful()) throw task.getException();
                if (task.getResult() == null)
                    throw new Exception("requestGet Failed, result is null");
                return Document.newInstance(itemClass, task.getResult());
            }
        });
    }

    /**
     * attachListenGet wait until Document with documentId is added to {@link DocumentsManager#list}
     *
     * @param lifecycleOwner         you and use {@link androidx.fragment.app.Fragment} or {@link androidx.appcompat.app.AppCompatActivity}
     * @param documentId             documentId
     * @param onValueChangedListener callback Document value when this Document is added
     */
    public void attachListen(LifecycleOwner lifecycleOwner, final String documentId, final Document.OnValueChangedListener<T> onValueChangedListener) {
        T data = get(documentId);
        if (data != null) {
            onValueChangedListener.onValueChanged(data);
            return;
        }
        attachListener(lifecycleOwner, new OnListChangedListener<T>() {
            @Override
            public void onItemInserted(int position, T data) {
                if (data.getId().equals(documentId)) onValueChangedListener.onValueChanged(data);
            }

            @Override
            public void onItemChanged(int position, T data) {
                if (data.getId().equals(documentId)) onValueChangedListener.onValueChanged(data);
            }

            @Override
            public void onItemRemoved(int position, T data) {
                if (data.getId().equals(documentId)) onValueChangedListener.onValueChanged(data);
            }

            @Override
            public void onDataSetChanged(ArrayList<T> list) {
                Integer index = mapIdWithIndex.get(documentId);
                if (index != null) {
                    T document = list.get(index);
                    if (document != null) onValueChangedListener.onValueChanged(document);
                }
            }
        });
    }

    /**
     * waitGet wait until Document with documentId is added to {@link DocumentsManager#list}
     * waitGet never return null and there are cases that waitGet never complete, use your own risk
     *
     * @param id documentId
     * @return
     */
    public Task<T> waitGet(final String id) {
        T data = get(id);
        if (data != null) {
            return TaskHelper.getCompletedTask(data);
        }
        final TaskCompletionSource<T> taskCompletionSource = new TaskCompletionSource<>();
        final OnListChangedListener<T> onListChangedListener = new OnListChangedListener<T>() {
            @Override
            public void onItemInserted(int position, T data) {
                if (data.getId().equals(id)) {
                    removeOnListChangedListener(this);
                    taskCompletionSource.setResult(data);
                }
            }

            @Override
            public void onDataSetChanged(ArrayList<T> list) {
                if (mapIdWithIndex.get(id) != null) {
                    removeOnListChangedListener(this);
                    taskCompletionSource.setResult(get(id));
                }
            }
        };
        addOnListChangedListener(onListChangedListener);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                removeOnListChangedListener(onListChangedListener);
                taskCompletionSource.setException(new RuntimeException("waitGet timeout! no response for 10 seconds"));
                Log.d(TAG, "[TIMEOUT] waitGet timeout after 10 seconds without any response");
            }
        }, 10000);
        return taskCompletionSource.getTask();
    }

    /**
     * query data to get
     * should only be called in baseDocumentsManager
     *
     * @param queryCreator
     * @return
     */
    public Task<List<T>> queryGet(QueryCreator queryCreator) {
        Query query = queryCreator.create(ref);
        return query.get().continueWith(new Continuation<QuerySnapshot, List<T>>() {
            @Override
            public List<T> then(@NonNull Task<QuerySnapshot> task) throws Exception {
                if (task.isSuccessful() && task.getResult() != null) {
                    QuerySnapshot querySnapshot = task.getResult();
                    List<T> queryDatas = new ArrayList<>();
                    for (DocumentSnapshot documentSnapshot : querySnapshot) {
                        T data = Document.newInstance(itemClass, documentSnapshot);
                        queryDatas.add(data);
                    }
                    return queryDatas;
                }
                throw task.getException();
            }
        });
    }

    protected T getFromParent(DocumentReference documentReference) {
        T data = get(documentReference.getId());
        if (data != null) {
            return data;
        } else {
            return listenNewDocument(documentReference);
        }
    }

    // LISTEN API
    @Nullable
    protected T listenNewDocument(DocumentReference ref) {
        try {
            T data = itemClass.newInstance();
            data.withRef(ref).withClass(itemClass);
            data.addDocumentsManager(this);
            data.setListenerRegistration(1000, null);
            return data;
        } catch (IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to listenNewDocument: itemClass#newInstance throw exception");
        }
    }

    // LIST API

    /**
     * put method
     * is called when a Document is added or updated to {@link DocumentsManager#list}
     *
     * @param data Document
     */
    @CallSuper
    public void put(T data) {
        String id = data.getId();
        Integer index = mapIdWithIndex.get(id);

        if (index != null) {
            update(index, data);
            onListChanged();
            for (OnListChangedListener<T> onListChangedListener : onListChangedListeners) {
                onListChangedListener.onItemChanged(index, data);
            }
        } else {
            add(id, data);
            onListChanged();
            for (int i = 0; i < onListChangedListeners.size(); i++) {
                OnListChangedListener<T> onListChangedListener = onListChangedListeners.get(i);
                onListChangedListener.onItemInserted(list.size() - 1, data);
                onListChangedListener.onListSizeChanged(list, list.size());
            }
        }
    }

    /**
     * add
     * is called when a Document is added to {@link DocumentsManager#list}
     *
     * @param id   documentId of Document
     * @param data new Document to add
     */
    @CallSuper
    public void add(String id, T data) {
        list.add(data);
        mapIdWithIndex.put(id, list.size() - 1);
    }

    /**
     * update
     * is called when a document is updated to {@link DocumentsManager#list}
     *
     * @param index index of Document in list array
     * @param data  Document contains updated value
     */
    @CallSuper
    public void update(int index, T data) {
        list.get(index).update(data);
    }

    @Nullable
    public T get(String id) {
        Integer index = mapIdWithIndex.get(id);
        if (index != null) {
            return list.get(index);
        }
        return null;
    }

    @Nullable
    public T remove(T document){
        int index = list.indexOf(document);
        return remove(list.get(index));
    }

    @Nullable
    public T remove(String id) {
        Integer index = mapIdWithIndex.get(id);
        if (index != null) {
            T data = list.get(index);
            data.removeDocumentsManager(this);
            list.remove(index.intValue());
            mapIdWithIndex.remove(id);
            for (int i = index; i < list.size(); i++) {
                mapIdWithIndex.put(list.get(i).getId(), i);
            }
            onListChanged();
            for (OnListChangedListener<T> onListChangedListener : onListChangedListeners) {
                onListChangedListener.onItemRemoved(index, data);
                onListChangedListener.onListSizeChanged(list, list.size());
            }
            return data;
        }
        return null;
    }

    public void clear() {
        list.clear();
        mapIdWithIndex.clear();
        onClear();
        for (OnListChangedListener<T> onListChangedListener : onListChangedListeners) {
            onListChangedListener.onDataSetChanged(list);
            onListChangedListener.onListSizeChanged(list, 0);
        }
    }

    public boolean contains(String documentId) {
        return mapIdWithIndex.get(documentId) != null;
    }

    public int indexOf(String id) {
        Integer index = mapIdWithIndex.get(id);
        return index != null ? index : -1;
    }

    public int indexOf(T document) {
        return indexOf(document.getId());
    }

    public ArrayList<T> getList() {
        return list;
    }

    // LISTENER

    public void addOnListChangedListener(@NonNull OnListChangedListener<T> listener) {
        this.onListChangedListeners.add(listener);
        listener.onDataSetChanged(list);
        listener.onListSizeChanged(list, list.size());
    }

    public void removeOnListChangedListener(@NonNull OnListChangedListener<T> listener) {
        this.onListChangedListeners.remove(listener);
    }

    public void attachSortedList(LifecycleOwner lifecycleOwner, final SortedList<T> sortedList) {
        final OnListChangedListener<T> listener = new OnListChangedListener<T>() {
            @Override
            public void onItemInserted(int position, T data) {
                try {
                    T dumpData = Documents.dumpValue(itemClass, data);
                    sortedList.add(dumpData);
                } catch (InstantiationException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onItemChanged(int position, T data) {
                try {
                    T dumpData = Documents.dumpValue(itemClass, data);
                    sortedList.add(dumpData);
                } catch (InstantiationException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onItemRemoved(int position, T data) {
                try {
                    T dumpData = Documents.dumpValue(itemClass, data);
                    sortedList.remove(dumpData);
                } catch (InstantiationException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onDataSetChanged(ArrayList<T> list) {
                if (list.size() == 0) sortedList.clear();
                try {
                    sortedList.addAll(Documents.dumpArrayValue(itemClass, list));
                } catch (IllegalAccessException | InstantiationException e) {
                    e.printStackTrace();
                }
            }
        };
        lifecycleOwner.getLifecycle().addObserver(new LifecycleObserver() {
            @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
            public void connectListener() {
                addOnListChangedListener(listener);
                listener.onDataSetChanged(list);
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            public void disconnectListener() {
                removeOnListChangedListener(listener);
            }
        });
    }

    public void attachAdapter(LifecycleOwner lifecycleOwner, final RecyclerView.Adapter adapter) {
        final OnListChangedListener<T> listener = new OnListChangedListener<T>() {
            @Override
            public void onItemInserted(int position, T data) {
                adapter.notifyItemInserted(position);
            }

            @Override
            public void onItemChanged(int position, T data) {
                adapter.notifyItemChanged(position);
            }

            @Override
            public void onItemRemoved(int position, T data) {
                adapter.notifyItemRemoved(position);
            }

            @Override
            public void onDataSetChanged(ArrayList<T> datas) {
                adapter.notifyDataSetChanged();
            }
        };
        attachListener(lifecycleOwner, listener);
    }

    public void attachListener(LifecycleOwner lifecycleOwner, @NonNull final OnListChangedListener<T> onListChangedListener) {
        lifecycleOwner.getLifecycle().addObserver(new LifecycleObserver() {
            @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
            public void connectListener() {
                addOnListChangedListener(onListChangedListener);
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            public void disconnectListener() {
                removeOnListChangedListener(onListChangedListener);
            }
        });
    }


    // INIT COMPLETE API
    public void waitUntilInitComplete(final OnInitCompleteListener<T> onInitCompleteListener) {
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

    public void removeOnInitCompleteListener(OnInitCompleteListener<T> onInitCompleteListener) {
        this.onInitCompleteListeners.remove(onInitCompleteListener);
    }

    public abstract boolean isListComplete();

    public void onClear() {
    }

    protected void onListChanged() {
        if (!isListComplete()) return;
        for (OnInitCompleteListener<T> onInitCompleteListener : onInitCompleteListeners) {
            onInitCompleteListener.onComplete(list);
            removeOnInitCompleteListener(onInitCompleteListener);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        Log.d(TAG, " successfully garbage collected");
    }

    // INTERFACE
    public interface OnDocumentGotListener<T extends Document> {
        void onGot(T data);
    }

    public interface QueryCreator {
        Query create(CollectionReference collectionReference);
    }

    public interface OnInitCompleteListener<T extends Document> {
        void onComplete(ArrayList<T> list);
    }

    public static class OnListChangedListener<T extends Document> {
        public void onItemInserted(int position, T data) {
        }

        public void onItemChanged(int position, T data) {
        }

        public void onItemRemoved(int position, T data) {
        }

        public void onDataSetChanged(ArrayList<T> list) {
        }

        public void onListSizeChanged(ArrayList<T> list, int size) {
        }
    }
}
