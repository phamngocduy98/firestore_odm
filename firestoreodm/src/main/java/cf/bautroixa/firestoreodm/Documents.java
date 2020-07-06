package cf.bautroixa.firestoreodm;

import java.util.ArrayList;
import java.util.List;

public class Documents {
    /**
     * dumpValue dump value from document into static data-Document object
     *
     * @param from  Document to dump
     * @param klass class of Document
     * @return static data-Document object
     * @throws IllegalAccessException by klass.newInstance
     * @throws InstantiationException by klass.newInstance
     */
    public static <T extends Document> T dumpValue(Class<T> klass, T from) throws InstantiationException, IllegalAccessException {
        T data = klass.newInstance();
        data.withClass(klass).withRef(from.getRef());
        data.update(from);
        return data;
    }

    /**
     * dumpArrayValue dump value from List <Document> into List of <static data-Document object>
     *
     * @param klass class of Document
     * @param from  List <Document> to dump
     * @param <T>   type of Document
     * @return List of <static data-Document object>
     * @throws IllegalAccessException by klass.newInstance
     * @throws InstantiationException by klass.newInstance
     */
    public static <T extends Document> List<T> dumpArrayValue(Class<T> klass, List<T> from) throws IllegalAccessException, InstantiationException {
        List<T> dumpList = new ArrayList<>();
        for (int i = 0; i < from.size(); i++) {
            T item = from.get(i);
            dumpList.add(dumpValue(klass, item));
        }
        return dumpList;
    }
}
