package cf.bautroixa.firestoreodm;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;

public class TaskHelper {
    public static <T extends Object> Task<T> getCompletedTask(T result) {
        TaskCompletionSource<T> taskCompletionSource = new TaskCompletionSource<>();
        taskCompletionSource.setResult(result);
        return taskCompletionSource.getTask();
    }
}
