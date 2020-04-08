package sd1920.trab1.core.resources.utils;

import sd1920.trab1.api.Message;

public class QueueLog {

    private Message m;
    private long mid;
    private String failedRecepiant;

    public String getFailedRecepiant() {
        return failedRecepiant;
    }

    public void setFailedRecepiant(String failedRecepiant) {
        this.failedRecepiant = failedRecepiant;
    }

    public long getMid() {
        return mid;
    }

    public void setMid(long mid) {
        this.mid = mid;
    }

    public Message getM() {
        return m;
    }

    public void setM(Message m) {
        this.m = m;
    }

}
