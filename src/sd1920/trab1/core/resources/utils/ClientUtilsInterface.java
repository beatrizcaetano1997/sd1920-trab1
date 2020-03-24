package sd1920.trab1.core.resources.utils;

import sd1920.trab1.api.Message;

import java.net.URI;

public interface ClientUtilsInterface {
    //In every method to verify the user
    boolean checkUser(URI uri, String user, String pwd);

    //When posting a message in other domain server ex -> postMessage
    Long postOtherDomainMessage(URI uri, Message message, String user);

    //Used to delete a given message in other domain ex -> DeleteMessage
    boolean deleteOtherDomainMessage(URI uri, String user, Message m);

    //Used to delete a user inbox ex -> DeleteUser
    boolean deleteUserInbox(URI uri, String user);
}
