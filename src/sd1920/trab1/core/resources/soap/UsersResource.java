package sd1920.trab1.core.resources.soap;

import sd1920.trab1.api.User;
import sd1920.trab1.api.soap.UserService;
import sd1920.trab1.api.soap.UsersException;
import sd1920.trab1.core.clt.soap.*;
import sd1920.trab1.core.servers.discovery.Discovery;

import javax.jws.WebService;
import javax.ws.rs.core.Response.Status;
import javax.xml.ws.WebServiceException;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.HashMap;
import java.util.logging.Logger;

@WebService(serviceName=UserService.NAME, 
targetNamespace=UserService.NAMESPACE, 
endpointInterface=UserService.INTERFACE)
public class UsersResource implements UserService
{
    private HashMap<String, User> users = new HashMap<>();

    private String domain;

    private static Logger Log = Logger.getLogger(MessageResource.class.getName());
    private Discovery discovery;

    public UsersResource(Discovery discovery, String domain)
    {
        this.domain = domain;
        this.discovery = discovery;
    }

    @Override
    public String postUser(User user) throws UsersException
    {
        if (user.getName() == null || user.getName().isEmpty()) {
            throw new UsersException(Status.CONFLICT);

        }

        if (user.getPwd() == null || user.getPwd().isEmpty()) {
            throw new UsersException(Status.CONFLICT);

        }

        if (user.getDomain() == null || user.getDomain().isEmpty()) {
            throw new UsersException(Status.CONFLICT);

        }

        if (user.getDisplayName() == null || user.getDisplayName().isEmpty()) {
            throw new UsersException(Status.CONFLICT);

        }


        if (users.containsKey(user.getName())) {
            throw new UsersException(Status.CONFLICT);
        }


        if (!user.getDomain().equals(domain)) {
            throw new UsersException(Status.FORBIDDEN);

        }

        synchronized (this) {
            users.put(user.getName(), user);
        }

        return user.getName() + "@" + user.getDomain();
    }

    @Override
    public User getUser(String name, String pwd) throws UsersException
    {
        synchronized (this) {
            if (!users.containsKey(name) || !users.get(name).getPwd().equals(pwd)) {
                throw new UsersException(Status.FORBIDDEN);
            } else {
                return users.get(name);
            }
        }
    }

    @Override
    public User updateUser(String name, String pwd, User user) throws UsersException
    {

        synchronized (this) {
            if (!users.containsKey(name) || !users.get(name).getPwd().equals(pwd)) {
                throw new UsersException(Status.FORBIDDEN);
            }
//            if (!users.get(name).getPwd().equals(pwd)) {
//                throw new UsersException(Status.CONFLICT);
//            }


            if (user.getPwd() != null) {
                users.get(name).setPwd(user.getPwd());
            }

            if (user.getDisplayName() != null) {
                users.get(name).setDisplayName(user.getDisplayName());
            }

//            updateOnWriteUsers();
        }

        synchronized (this) {
            return users.get(name);
        }
    }

    @Override
    public String checkIfUserExists(String user) throws UsersException
    {
        if (users.containsKey(user)) {
            return users.get(user).getDisplayName();
        } else
            throw new UsersException(Status.NOT_FOUND);
    }

    @Override
    public User deleteUser(String user, String pwd) throws UsersException
    {
        synchronized (this)
        {
            if (!users.containsKey(user) || !users.get(user).getPwd().equals(pwd))
                throw new UsersException(Status.FORBIDDEN);

            boolean success = false;
            
            try
            {
            	success = new ClientUtilsMessages(getURI(domain, "messages").toString()).deleteUserInbox(user);
            }
            catch (MalformedURLException | WebServiceException clientEx)
            {
            	throw new UsersException(clientEx.getMessage());
            }
            
            if (success)
            	return users.remove(user);
            else
            	return null;
        }
    }

    //UTILS
    public URI getURI(String domain, String serviceType)
    {

    	return discovery.getURI(domain, serviceType);
    }

}
