package sd1920.trab1.core.clt.soap;

import sd1920.trab1.api.Message;

import java.net.URI;

public interface IClientUtilsMessages {

    //When posting a message in other domain server ex -> postMessage
    Long postOtherDomainMessage(Message message, String user);

    //Used to delete a given message in other domain ex -> DeleteMessage
    boolean deleteOtherDomainMessage(String user, Message m);

    //Used to delete a user inbox ex -> DeleteUser
    boolean deleteUserInbox(String user);
}
