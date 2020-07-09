package cf.bautroixa.firestoreodm;

import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.OnLifecycleEvent;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Objects;

public abstract class Document implements Serializable {
    @Exclude
    public static final String ID = "id";
    @Exclude
    protected String TAG = "Document";
    @Exclude
    @Nullable
    protected OnValueChangedListener initListener = null;
    @Exclude
    protected DocumentReference ref;
    @Exclude
    private ListenerRegistration listenerRegistration;
    @Exclude
    private Class klass;
    @Exclude
    private ArrayList<OnValueChangedListener> onNewValueListeners = new ArrayList<>();
    @Exclude
    private ArrayList<DocumentsManager> documentsManagers = new ArrayList<>();
    @Exclude
    private boolean isListening = false, isRemoved = false, isAvailable = false, isSubManagerAvailable = false;

    /**
     * empty constructor
     * {@link DocumentSnapshot#toObject(Class)} use this constructor to build object
     */
    public Document() {
    }

    /**
     * construct new Document with DocumentSnapshot
     * newly constructed object is added its DocumentReference
     *
     * @param klass            Class
     * @param documentSnapshot DocumentSnapshot
     * @return Document object type T
     */
    @Exclude
    public static <T extends Document> T newInstance(Class<T> klass, DocumentSnapshot documentSnapshot) {
        T data = documentSnapshot.toObject(klass);
        data.withRef(documentSnapshot.getReference()).withClass(klass);
        data.setAvailable(true);
        return data;
    }

    /**
     * withClass add Class to object
     *
     * @param klass Class
     * @return this object
     */
    @Exclude
    public <T extends Document> T withClass(Class<T> klass) {
        this.klass = klass;
        TAG = klass.getSimpleName();
        return (T) this;
    }

    /**
     * withRef add DocumentReference to object
     *
     * @param ref DocumentReference
     * @return this object
     */
    @Exclude
    public <T extends Document> T withRef(@NonNull DocumentReference ref) {
        this.ref = ref;
        return (T) this;
    }

    /**
     * update new value from other Document object
     *
     * @param document other Document object that contains new value to update
     */
    @Exclude
    protected abstract void update(Document document);


    @Exclude
    public void setListenerRegistration(final long retryInterval, @Nullable final OnValueChangedListener initListener) {
        if (listenerRegistration != null) {
            // TODO: test with onRemove too
            cancelListenerRegistration();
        }
        this.initListener = initListener;
        final Document thisDocument = this;
        this.listenerRegistration = this.ref.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot documentSnapshot, @Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    Log.e(TAG, String.format("[Retry in %d ms] Listen %s failed reason: %s", retryInterval, ref.getId(), e.getMessage()));
                    isListening = false;
                    if (retryInterval >= 0 && retryInterval < 60 * 1000) {
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                setListenerRegistration(retryInterval * 2, initListener);
                            }
                        }, retryInterval);
                    } else {
                        Log.e(TAG, String.format("[TIMEOUT] Listen %s failed reason: %s", ref.getId(), e.getMessage()));
                    }
                    return;
                }
                isListening = true;
                if (documentSnapshot != null) {
                    if (documentSnapshot.exists()) {
                        isAvailable = true;
                        update(newInstance(klass, documentSnapshot));
                        for (DocumentsManager documentsManager : documentsManagers) {
                            documentsManager.put(thisDocument);
                        }
                    } else {
                        for (DocumentsManager documentsManager : documentsManagers) {
                            documentsManager.remove(thisDocument);
                        }
                        onRemove();
                    }
                    if (initListener != null) {
                        initListener.onValueChanged(thisDocument);
                    }
                    for (OnValueChangedListener listener : onNewValueListeners) {
                        listener.onValueChanged(thisDocument);
                    }
                }
            }
        });
    }

    @Exclude
    public void cancelListenerRegistration() {
        if (listenerRegistration != null) listenerRegistration.remove();
        listenerRegistration = null;
    }

    @Exclude
    public void onRemove() {
        this.isAvailable = false;
        this.isRemoved = true;
        cancelListenerRegistration();
        this.documentsManagers.clear();
        for (OnValueChangedListener listener : onNewValueListeners) {
            listener.onValueChanged(this);
        }
    }

    // UPDATE OR DELETE DOCUMENT

    /**
     * Update document to Firebase cloud firestore
     *
     * @param batch               Writebatch (optional)
     * @param field               field name (property name) to update
     * @param value               value of field to update
     * @param moreFieldsAndValues more field and value
     * @return Task
     */
    @Exclude
    public Task<Void> sendUpdate(@Nullable WriteBatch batch, @NonNull String field, @Nullable Object value, Object... moreFieldsAndValues) {
        if (batch != null) {
            batch.update(this.ref, field, value, moreFieldsAndValues);
            TaskCompletionSource<Void> source = new TaskCompletionSource<Void>();
            return source.getTask();
        }
        return this.ref.update(field, value, moreFieldsAndValues);
    }

    /**
     * Delete document from Firebase cloud firestore
     *
     * @param batch Writebatch (optional)
     * @return Task
     */
    @Exclude
    public Task<Void> sendDelete(@Nullable WriteBatch batch) {
        if (batch != null) {
            batch.delete(this.ref);
            TaskCompletionSource<Void> source = new TaskCompletionSource<Void>();
            return source.getTask();
        }
        return this.ref.delete();
    }

    // LISTENER
    @Exclude
    public void addOnNewValueListener(OnValueChangedListener listener) {
        this.onNewValueListeners.add(listener);
        listener.onValueChanged(this);
    }

    @Exclude
    public void removeOnNewValueListener(OnValueChangedListener listener) {
        this.onNewValueListeners.remove(listener);
    }

    @Exclude
    public void attachListener(LifecycleOwner lifecycleOwner, final OnValueChangedListener listener) {
        lifecycleOwner.getLifecycle().addObserver(new LifecycleObserver() {
            @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
            public void connectListener() {
                addOnNewValueListener(listener);
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            public void disconnectListener() {
                removeOnNewValueListener(listener);
            }
        });
    }

    // GETTER, SETTER
    @Exclude
    public String getId() {
        return ref != null ? ref.getId() : null;
    }

    @Exclude
    public DocumentReference getRef() {
        return ref;
    }

    @Exclude
    public Class getKlass() {
        return klass;
    }

    @Exclude
    public boolean isRemoved() {
        return isRemoved;
    }

    @Exclude
    public void setRemoved(boolean removed) {
        isRemoved = removed;
    }

    @Exclude
    public boolean isListening() {
        return isListening;
    }

    @Exclude
    public void setListening(boolean listening) {
        isListening = listening;
    }

    @Exclude
    public boolean isSubManagerAvailable() {
        return isSubManagerAvailable;
    }

    @Exclude
    public void setSubManagerAvailable(boolean subManagerAvailable) {
        isSubManagerAvailable = subManagerAvailable;
    }

    @Exclude
    public boolean isAvailable() {
        return isAvailable;
    }

    @Exclude
    public void setAvailable(boolean available) {
        isAvailable = available;
    }

    // EQUALS
    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Document document = (Document) obj;
        return Objects.equals(getId(), document.getId()) && Objects.equals(ref, document.getRef());
    }

    @Override
    protected void finalize() throws Throwable {
        Log.d(TAG, " successfully garbage collected documentId = " + getId());
    }

    @Exclude
    public void addDocumentsManager(DocumentsManager documentsManager) {
        if (!documentsManagers.contains(documentsManager)) {
            documentsManagers.add(documentsManager);
        }
        if (isAvailable) {
            documentsManager.put(this);
        }
    }

    @Exclude
    public void removeDocumentsManager(DocumentsManager documentsManager) {
        documentsManagers.remove(documentsManager);
    }

    public interface OnValueChangedListener<T extends Document> {
        /**
         * onNewData
         *
         * @param data new value
         */
        void onValueChanged(@NonNull T data);
    }
}
