package cf.bautroixa.firestorehelper.model;

import cf.bautroixa.firestoreodm.Document;

public class Notification extends Document {
    private String content;

    @Override
    protected void update(Document document) {
        Notification notification = (Notification) document;
        content = notification.getContent();
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
