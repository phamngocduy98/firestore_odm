package cf.bautroixa.firestoreodm;

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
 * @author PhamNgocDuy
 * @version 0.0.1
 * @since 2020/07/07
 * @param <T> extends Document, Document type
 */
public class DocumentsManager<T extends Document> {
    private String TAG = "Manager";
    protected CollectionReference ref;
    protected Class<T> itemClass;
    protected HashMap<String, Integer> mapIdWithIndex;
    protected ArrayList<T> list;
    protected ArrayList<OnListChangedListener<T>> onListChangedListeners;
    protected boolean isListening = true;

    public DocumentsManager(Class<T> itemClass) {
        this.itemClass = itemClass;
        TAG = itemClass.getSimpleName() + TAG;
        this.mapIdWithIndex = new HashMap<>();
        this.list = new ArrayList<>();
        this.onListChangedListeners = new ArrayList<>();
    }

    public DocumentsManager(Class<T> itemClass, CollectionReference collectionReference) {
        this.ref = collectionReference;
        this.itemClass = itemClass;
        TAG = itemClass.getSimpleName() + TAG;
        this.mapIdWithIndex = new HashMap<>();
        this.list = new ArrayList<>();
        this.onListChangedListeners = new ArrayList<>();
    }

    public boolean isListening() {
        return isListening;
    }

    protected void setListening(boolean listening) {
        isListening = listening;
    }

    public CollectionReference getRef() {
        return ref;
    }

    /**
     * create a new document in collection
     * @param batch create document in this WriteBatch
     * @param data
     *      Document to create, use {@link Document#withRef(DocumentReference)}
     *      to specify a custom DocumentReference or documentId
     * @return DocumentReference to newly created document
     */
    public DocumentReference create(WriteBatch batch, T data) {
        DocumentReference newDataRef = data.getId() != null ? ref.document(data.getId()) : ref.document();
        batch.set(newDataRef, data);
        return newDataRef;
    }

    /**
     * create a new document in collection
     * @param data
     *      Document to create, use {@link Document#withRef(DocumentReference)}
     *      to specify a custom DocumentReference or documentId
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

    public void delete(@NonNull WriteBatch batch, String id) {
        DocumentReference dataRef = ref.document(id);
        batch.delete(dataRef);
    }

    public Task<Void> delete(String id) {
        DocumentReference dataRef = ref.document(id);
        return dataRef.delete();
    }

    /**
     * put method
     * is called when a Document is added or updated to {@link DocumentsManager#list}
     * @param data Document
     */
    @CallSuper
    public void put(T data) {
        String id = data.getId();
        Integer index = mapIdWithIndex.get(id);

        if (index != null) {
            update(index, data);
            for (OnListChangedListener<T> onListChangedListener : onListChangedListeners) {
                onListChangedListener.onItemChanged(index, data);
            }
        } else {
            add(id, data);
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
     * @param id documentId of Document
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
     * @param index index of Document in list array
     * @param data Document contains updated value
     */
    @CallSuper
    public void update(int index, T data) {
        list.get(index).update(data);
    }


    public T rawPut(DocumentSnapshot documentSnapshot) {
        T data = Document.newInstance(itemClass, documentSnapshot);
        put(data);
        return data;
    }

    @Nullable
    public T get(String id) {
        Integer index = mapIdWithIndex.get(id);
        if (index != null) {
            return list.get(index);
        }
        return null;
    }

    /**
     * requestGet get a document with documentId
     * @param id documentId to get
     * @return Task contains Document value
     */
    public Task<T> requestGet(String id) {
        T data = get(id);
        if (data != null) {
            return TaskHelper.getCompletedTask(data);
        }
        return ref.document(id).get().continueWith(new Continuation<DocumentSnapshot, T>() {
            @Override
            @Nullable
            public T then(@NonNull Task<DocumentSnapshot> task) throws Exception {
                if (task.isSuccessful() && task.getResult() != null) {
                    return Document.newInstance(itemClass, task.getResult());
                }
                Log.e(TAG, "requestGet task failed, return value is NULL");
                throw task.getException();
            }
        });
    }

    protected void requestListen(String id, final Document.OnValueChangedListener<T> onGotValue) {
        T data = get(id);
        if (data != null) {
            onGotValue.onValueChanged(data);
        }
        listenNewDocument(ref.document(id), onGotValue);
    }

    protected void listenNewDocument(DocumentReference ref) {
        listenNewDocument(ref, null);
    }

    protected void listenNewDocument(DocumentReference ref, @Nullable Document.OnValueChangedListener<T> onGotValue) {
        try {
            T data = itemClass.newInstance();
            data.withRef(ref).withClass(itemClass);
            data.setListenerRegistration(1000, this, onGotValue);
        } catch (IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
        }
    }

    /**
     * attachListenGet wait until Document with documentId is added to {@link DocumentsManager#list}
     * @param lifecycleOwner you and use {@link androidx.fragment.app.Fragment} or {@link androidx.appcompat.app.AppCompatActivity}
     * @param id documentId
     * @param onDocumentGotListener callback Document value when this Document is added
     */
    public void attachListenGet(LifecycleOwner lifecycleOwner, final String id, final OnDocumentGotListener<T> onDocumentGotListener) {
        T data = get(id);
        if (data != null) {
            onDocumentGotListener.onGot(data);
            return;
        }
        attachListener(lifecycleOwner, new OnListChangedListener<T>() {
            @Override
            public void onItemInserted(int position, T data) {
                if (data.getId().equals(id)) onDocumentGotListener.onGot(data);
            }

            @Override
            public void onItemChanged(int position, T data) {

            }

            @Override
            public void onItemRemoved(int position, T data) {

            }

            @Override
            public void onDataSetChanged(ArrayList<T> list) {
                if (mapIdWithIndex.get(id) != null) onDocumentGotListener.onGot(get(id));
            }
        });
    }

    public void oneTimeListenGet(final String id, final OnDocumentGotListener<T> onDocumentGotListener) {
        T data = get(id);
        if (data != null) {
            onDocumentGotListener.onGot(data);
            return;
        }
        addOnListChangedListener(new OnListChangedListener<T>() {
            @Override
            public void onItemInserted(int position, T data) {
                if (data.getId().equals(id)) {
                    removeOnListChangedListener(this);
                    onDocumentGotListener.onGot(data);
                }
            }

            @Override
            public void onItemChanged(int position, T data) {

            }

            @Override
            public void onItemRemoved(int position, T data) {

            }

            @Override
            public void onDataSetChanged(ArrayList<T> list) {
                if (mapIdWithIndex.get(id) != null) {
                    removeOnListChangedListener(this);
                    onDocumentGotListener.onGot(get(id));
                }
            }
        });
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
                        // TODO: save got data to list, remember that, it should be saved in baseDatasManager to prevent unwanted item in list
                        //put(data);
                    }
                    return queryDatas;
                }
                throw task.getException();
            }
        });
    }

    @Nullable
    public T remove(String id) {
        Integer index = mapIdWithIndex.get(id);
        if (index != null) {
            T data = list.get(index);
//            TODO: data.onRemove() in sub-ref-arr-manager is unsafe
            list.remove(index.intValue());
            mapIdWithIndex.remove(id);
            for (int i = index; i < list.size(); i++) {
                mapIdWithIndex.put(list.get(i).getId(), i);
            }
            for (OnListChangedListener<T> onListChangedListener : onListChangedListeners) {
                onListChangedListener.onItemRemoved(index, data);
                onListChangedListener.onListSizeChanged(list, list.size());
            }
            return data;
        }
        return null;
    }

    public boolean contains(String id) {
        return mapIdWithIndex.get(id) != null;
    }

    public int indexOf(String id) {
        Integer index = mapIdWithIndex.get(id);
        return index != null ? index : -1;
    }

    public int indexOf(Document document) {
        return indexOf(document.getId());
    }

    public DocumentReference getDocumentReference(String documentId) {
        if (documentId == null) return ref.document();
        return ref.document(documentId);
    }

    public DocumentReference getNewDocumentReference() {
        return ref.document();
    }

    public ArrayList<T> getList() {
        return list;
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

    public void onClear() {

    }

    public DocumentsManager<T> addOnListChangedListener(@NonNull OnListChangedListener<T> listener) {
        this.onListChangedListeners.add(listener);
        listener.onDataSetChanged(list);
        listener.onListSizeChanged(list, list.size());
        return this;
    }

    public DocumentsManager<T> removeOnListChangedListener(@NonNull OnListChangedListener<T> listener) {
        this.onListChangedListeners.remove(listener);
        return this;
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

    public ArrayList<OnListChangedListener<T>> getListeners() {
        return this.onListChangedListeners;
    }

    public void restoreListeners(ArrayList<OnListChangedListener<T>> backupListeners) {
        this.onListChangedListeners.addAll(backupListeners);
        for (int i = 0; i < backupListeners.size(); i++) {
            backupListeners.get(i).onDataSetChanged(this.list);
        }
    }

    public interface OnDocumentGotListener<T extends Document> {
        void onGot(T data);
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

    public interface QueryCreator {
        Query create(CollectionReference collectionReference);
    }

    public interface OnInitCompleteListener<T extends Document> {
        void onComplete(ArrayList<T> list);
    }
}
