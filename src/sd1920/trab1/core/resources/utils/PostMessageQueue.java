package sd1920.trab1.core.resources.utils;

import sd1920.trab1.api.Message;
import sd1920.trab1.core.resources.MessageResource;

import java.util.concurrent.ConcurrentLinkedQueue;

public class PostMessageQueue implements Runnable {

    ConcurrentLinkedQueue<QueueLog> queue = new ConcurrentLinkedQueue<>();
    MessageResource mr;

    public PostMessageQueue(MessageResource mr) {
        this.mr = mr;
    }

    public void addMessage(Message m, long newID, String recipient) {
        QueueLog qlog = new QueueLog();
        qlog.setFailedRecepiant(recipient);
        qlog.setMid(newID);
        qlog.setM(m);
        queue.add(qlog);
    }

    @Override
    public void run() {
        for (; ; ) {
            if (!queue.isEmpty()) {
                QueueLog m = queue.poll();
                String userDomain = m.getFailedRecepiant().split("@")[1];
                long mid = mr.clientUtils.postOtherDomainMessage(mr.getURI(userDomain, "messages"), m.getM(), m.getFailedRecepiant());
                if (mid == -1) {
                    queue.add(m);
                } else {
                    mr.deleteFailedMessage(m.getMid(), m.getM().getSender(), m.getFailedRecepiant());
                }
            }
        }
    }
}
