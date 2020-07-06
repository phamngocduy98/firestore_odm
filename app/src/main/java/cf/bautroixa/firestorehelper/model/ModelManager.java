package cf.bautroixa.firestorehelper.model;

import com.google.firebase.firestore.FirebaseFirestore;

import cf.bautroixa.firestoreodm.CollectionManager;

/**
 * Create singleton ModelManager to manage all Document object
 */
public class ModelManager {
    private static ModelManager mInstance = null;
    private FirebaseFirestore db;
    private CollectionManager<User> userCollectionManager;

    private ModelManager() {
        // initiate your firebase firestore instance
        db = FirebaseFirestore.getInstance();
        // add CollectionManager to manage your firestore collection
        userCollectionManager = new CollectionManager<>(User.class, db.collection("user_collection_name"));
    }

    public static ModelManager getInstance() {
        if (mInstance == null) {
            synchronized (ModelManager.class) {
                if (mInstance == null) {
                    mInstance = new ModelManager();
                }
            }
        }
        return mInstance;
    }

    // remember to add getter to your collection manager
    public CollectionManager<User> getUserCollectionManager() {
        return userCollectionManager;
    }
}
