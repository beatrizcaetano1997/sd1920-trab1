package sd1920.trab1.core.resources.utils;

import sd1920.trab1.api.Message;
import sd1920.trab1.core.resources.MessageResource;

import java.util.concurrent.ConcurrentLinkedQueue;

public class DeleteMessageQueue implements Runnable{

    ConcurrentLinkedQueue<QueueLog> queue = new ConcurrentLinkedQueue<>();
    MessageResource mr;

    public DeleteMessageQueue(MessageResource mr) {
        this.mr = mr;
    }

    public void addMessage(Message m, String recipient){
        QueueLog qlog = new QueueLog();
        qlog.setFailedRecepiant(recipient);
        qlog.setM(m);
        queue.add(qlog);
    }

    @Override
    public void run() {
        for(;;){
            if(!queue.isEmpty()){
                QueueLog m = queue.poll();
                String userDomain = m.getFailedRecepiant().split("@")[1];
                boolean success =  mr.clientUtils.deleteOtherDomainMessage(mr.getURI(userDomain, "messages"), m.getFailedRecepiant(), m.getM());
                if(!success){
                    queue.add(m);
                }
            }
        }
    }
}
