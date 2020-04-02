package sd1920.trab1.core.clt.soap;

import sd1920.trab1.api.User;

import java.net.URI;

public interface IClientUtilsUsers {
    //In every method to verify the user
    User checkUser(URI uri, String user, String pwd);
}
