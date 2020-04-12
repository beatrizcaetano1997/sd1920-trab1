package sd1920.trab1.core.clt.rest;

import sd1920.trab1.api.Message;
import sd1920.trab1.api.User;

import java.net.URI;

public interface ClientUtilsInterface {
    //In every method to verify the user
    User checkUser(String user, String pwd);

    //When posting a message in other domain server ex -> postMessage
    Long postOtherDomainMessage(Message message, String user);

    //Used to delete a given message in other domain ex -> DeleteMessage
    void deleteOtherDomainMessage(String user, Message m);

    //Used to delete a user inbox ex -> DeleteUser
    void deleteUserInbox(String user);
    
    String userExists(String user);
}
