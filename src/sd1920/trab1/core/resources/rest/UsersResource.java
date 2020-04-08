package sd1920.trab1.core.resources.rest;

import sd1920.trab1.api.User;
import sd1920.trab1.api.rest.UserService;
import sd1920.trab1.core.clt.rest.ClientUtils;
import sd1920.trab1.core.resources.soap.MessageResource;
import sd1920.trab1.core.servers.discovery.Discovery;

import javax.jws.WebService;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import java.io.*;
import java.net.URI;
import java.util.HashMap;
import java.util.logging.Logger;

public class UsersResource implements UserService {

    private HashMap<String, User> users = new HashMap<>();

    private String domain;

    private static Logger Log = Logger.getLogger(MessageResource.class.getName());
    private Discovery discovery;
    private ClientUtils clientUtils;

    public UsersResource(Discovery discovery, String domain) {
        this.domain = domain;
        this.discovery = discovery;
        clientUtils = new ClientUtils();
    }

    @Override
    public String postUser(User user) {


        if (user.getName() == null || user.getName().isEmpty()) {
            throw new WebApplicationException(Status.CONFLICT);

        }

        if (user.getPwd() == null || user.getPwd().isEmpty()) {
            throw new WebApplicationException(Status.CONFLICT);

        }

        if (user.getDomain() == null || user.getDomain().isEmpty()) {
            throw new WebApplicationException(Status.CONFLICT);

        }

        if (user.getDisplayName() == null || user.getDisplayName().isEmpty()) {
            throw new WebApplicationException(Status.CONFLICT);

        }


        if (users.containsKey(user.getName())) {
            throw new WebApplicationException(Status.CONFLICT);
        }


        if (!user.getDomain().equals(domain)) {
            throw new WebApplicationException(Status.FORBIDDEN);

        }

        synchronized (this) {
            users.put(user.getName(), user);
        }

        return user.getName() + "@" + user.getDomain();
    }

    @Override
    public User getUser(String name, String pwd) {
        synchronized (this) {
            if (!users.containsKey(name) || !users.get(name).getPwd().equals(pwd)) {
                throw new WebApplicationException(Status.FORBIDDEN);
            } else {
                return users.get(name);
            }
        }
    }

    @Override
    public User updateUser(String name, String pwd, User user) {

        synchronized (this) {
            if (!users.containsKey(name) || !users.get(name).getPwd().equals(pwd)) {
                throw new WebApplicationException(Status.FORBIDDEN);
            }
//            if (!users.get(name).getPwd().equals(pwd)) {
//                throw new WebApplicationException(Status.CONFLICT);
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
    public String checkIfUserExists(String user) {
        if (users.containsKey(user)) {
            return users.get(user).getDisplayName();
        } else
            throw new WebApplicationException(Status.NOT_FOUND);
    }

    @Override
    public User deleteUser(String user, String pwd)
    {
        synchronized (this)
        {
            if (!users.containsKey(user) || !users.get(user).getPwd().equals(pwd))
                throw new WebApplicationException(Status.FORBIDDEN);

            clientUtils.deleteUserInbox(discovery.getURI(domain, "messages"), user);

            return users.remove(user);
        }
    }
    
    //UTILS
    public URI getURI(String domain, String serviceType)
    {
    	return discovery.getURI(domain, serviceType);
    }

}
