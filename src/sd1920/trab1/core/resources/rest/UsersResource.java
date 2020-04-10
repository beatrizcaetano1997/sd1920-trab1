package sd1920.trab1.core.resources.rest;

import sd1920.trab1.api.User;
import sd1920.trab1.api.rest.UserService;
import sd1920.trab1.core.clt.rest.ClientUtils;
import sd1920.trab1.core.servers.discovery.Discovery;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import java.net.URI;
import java.util.HashMap;

public class UsersResource implements UserService {

    private final HashMap<String, User> users = new HashMap<>();

    private String domain;

    private Discovery discovery;
    private ClientUtils clientUtils;

    public UsersResource(String domain, Discovery discovery) {
        this.domain = domain;
        this.discovery = discovery;
        clientUtils = new ClientUtils();
    }

    @Override
    public String postUser(User user) {
        User chk;

        synchronized (this) {
            chk = users.get(user.getName());
        }

        if (user.getName() == null || user.getName().isEmpty() || user.getPwd() == null || user.getPwd().isEmpty()
                || user.getDomain() == null || user.getDomain().isEmpty() || user.getDisplayName() == null || user.getDisplayName().isEmpty()
                || chk != null) {

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
        User user;

        synchronized (this) {
            user = users.get(name);
        }

        if (user == null || !user.getPwd().equals(pwd)) {
            throw new WebApplicationException(Status.FORBIDDEN);

        } else {
            return user;
        }

    }

    @Override
    public User updateUser(String name, String pwd, User user) {

        User chk;

        synchronized (this) {
            chk = users.get(name);
        }

        if (chk == null || !chk.getPwd().equals(pwd)) {
            throw new WebApplicationException(Status.FORBIDDEN);
        }

        if (user.getPwd() != null) {
            synchronized (this) {
                users.get(name).setPwd(user.getPwd());
            }
        }

        if (user.getDisplayName() != null) {
            synchronized (this) {
                users.get(name).setDisplayName(user.getDisplayName());
            }
        }


        synchronized (this) {
            return users.get(name);
        }
    }

    @Override
    public String checkIfUserExists(String user) {
        User chk;

        synchronized (this) {
            chk = users.get(user);
        }

        if (chk != null) {
            return chk.getDisplayName();
        } else
            throw new WebApplicationException(Status.NOT_FOUND);

    }

    @Override
    public User deleteUser(String user, String pwd) {
        User uncheck;
        synchronized (this) {
            uncheck = users.get(user);
        }

        if (uncheck == null || !uncheck.getPwd().equals(pwd)) {
            throw new WebApplicationException(Status.FORBIDDEN);
        }

        clientUtils.deleteUserInbox(getURI(domain), user);

        synchronized (this) {
            uncheck = users.remove(user);
        }
        return uncheck;

    }

    private URI getURI(String domain) {

        URI[] l = discovery.knownUrisOf(domain);
        for (URI uri : l) {
            if (uri.toString().contains("rest")) {
                return URI.create(uri.toString() + "/messages");
            }
        }
        return null;
    }


}
