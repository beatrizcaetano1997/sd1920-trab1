package sd1920.trab1.core.resources;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import sd1920.trab1.api.Message;
import sd1920.trab1.api.User;
import sd1920.trab1.api.rest.UserService;
import sd1920.trab1.core.servers.discovery.Discovery;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response.Status;
import java.io.*;
import java.net.URI;
import java.util.HashMap;
import java.util.Random;
import java.util.logging.Logger;

public class UsersResource implements UserService {

    private static final int CONNECTION_TIMEOUT = 10000;
    private static final int REPLY_TIMOUT = 600;

    private HashMap<String, User> users;

    private String domain;

    private static Logger Log = Logger.getLogger(MessageResource.class.getName());
    private Discovery discovery;

    public UsersResource(String domain, Discovery discovery) {
        this.domain = domain;
        this.discovery = discovery;
        deserializeUsers();
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
            updateOnWriteUsers();
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
            if (!users.containsKey(name)) {
                throw new WebApplicationException(Status.NOT_FOUND);
            }
            if (!users.containsKey(name) && !users.get(name).getPwd().equals(pwd)) {
                throw new WebApplicationException(Status.CONFLICT);
            }


            if (user.getPwd() != null) {
                users.get(name).setPwd(user.getPwd());
            }

            if (user.getDisplayName() != null) {
                users.get(name).setDisplayName(user.getDisplayName());
            }

            updateOnWriteUsers();
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

            webTarget(domain, "messages").path("/deleteUserInbox/" + user).request().delete();
            User removed = users.remove(user);
            updateOnWriteUsers();

            return removed;
        }
    }

    private WebTarget webTarget(String domain, String serviceType) {

        ClientConfig config = new ClientConfig();
        config.property(ClientProperties.CONNECT_TIMEOUT, CONNECTION_TIMEOUT);
        config.property(ClientProperties.READ_TIMEOUT, REPLY_TIMOUT);
        Client client = ClientBuilder.newClient(config);
        URI[] l = discovery.knownUrisOf(domain);
        for (URI uri : l) {
            if (uri.toString().contains(serviceType)) {
                return client.target(uri);
            }
        }
        return null;
    }

    private void updateOnWriteUsers() {
        FileOutputStream fileOut = null;
        try {
            fileOut = new FileOutputStream("users.ser");
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(users);
            out.close();
            fileOut.close();
        } catch (IOException e) {
            //e.printStackTrace();
        }

    }

    private void deserializeUsers() {
        try {
            FileInputStream fileIn = new FileInputStream("users.ser");
            ObjectInputStream objIn = new ObjectInputStream(fileIn);
            users = (HashMap<String, User>) (objIn.readObject());
            objIn.close();
            fileIn.close();
        } catch (FileNotFoundException fnf) {
            users = new HashMap<>();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
