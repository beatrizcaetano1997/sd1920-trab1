package sd1920.trab1.api.resources;

import sd1920.trab1.api.User;
import sd1920.trab1.api.rest.UserService;
import sd1920.trab1.api.servers.discovery.Discovery;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import javax.ws.rs.core.Response.Status;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class UsersResource implements UserService {

    private Random randomNumberGenerator;

    private final HashMap<String, User> users = new HashMap<>();

    private String domain;

    private static Logger Log = Logger.getLogger(MessageResource.class.getName());

    public UsersResource(String domain) {
        this.domain = domain;
    }

    @Override
    public String postUser(User user) {
        if (!user.getDomain().equals(domain)) {
            throw new WebApplicationException(Status.FORBIDDEN);
        }

        if (user.getPwd() == null || user.getName() == null || user.getDomain() == null) {
            throw new WebApplicationException(Status.CONFLICT);
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
                throw new WebApplicationException(Status.CONFLICT);
            } else {
                return users.get(name);
            }
        }
    }

    @Override
    public User updateUser(String name, String pwd, User user) {

        synchronized (this) {
            if(!users.containsKey(name)){
                throw new WebApplicationException(Status.NOT_FOUND);
            }
            if (!users.containsKey(name) && !users.get(name).getPwd().equals(pwd)) {
                throw new WebApplicationException(Status.CONFLICT);
            }
        }

        if (user.getPwd() != null) {
            users.get(name).setPwd(user.getPwd());
        }

        if (user.getDisplayName() != null){
            users.get(name).setDisplayName(user.getDisplayName());
        }

        synchronized (this) {
            return users.get(name);
        }
    }

    @Override
    public User deleteUser(String user, String pwd) {
        synchronized (this) {
            if (!users.containsKey(user) && !users.get(user).getPwd().equals(pwd)) {
                throw new WebApplicationException(Status.CONFLICT);
            }

            return users.remove(user);
        }
    }
}
