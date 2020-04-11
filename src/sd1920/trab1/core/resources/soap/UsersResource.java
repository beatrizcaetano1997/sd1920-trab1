package sd1920.trab1.core.resources.soap;

import sd1920.trab1.api.User;
import sd1920.trab1.api.soap.UserServiceSoap;
import sd1920.trab1.core.clt.soap.*;
import sd1920.trab1.core.servers.discovery.Discovery;
import sd1920.trab1.api.soap.MessageServiceSoap;
import sd1920.trab1.api.soap.MessagesException;

import javax.jws.WebService;
import javax.ws.rs.core.Response.Status;
import javax.xml.ws.WebServiceException;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.HashMap;
import java.util.logging.Logger;

@WebService(serviceName=UserServiceSoap.NAME, 
targetNamespace=UserServiceSoap.NAMESPACE, 
endpointInterface=UserServiceSoap.INTERFACE)
public class UsersResource implements UserServiceSoap {

	private static Logger Log = Logger.getLogger(UsersResource.class.getName());
	
    private final HashMap<String, User> users = new HashMap<>();

    private String domain;

    private Discovery discovery;

    public UsersResource(Discovery discovery, String domain) {
        this.domain = domain;
        this.discovery = discovery;
    }

    @Override
    public String postUser(User user) throws MessagesException
    {
        User chk;

        synchronized (this) {
            chk = users.get(user.getName());
        }

        if (user.getName() == null || user.getName().isEmpty() || user.getPwd() == null || user.getPwd().isEmpty()
                || user.getDomain() == null || user.getDomain().isEmpty() || user.getDisplayName() == null || user.getDisplayName().isEmpty()
                || chk != null) {

        	throw new MessagesException(Status.CONFLICT);
        }


        if (!user.getDomain().equals(domain)) {

        	throw new MessagesException(Status.FORBIDDEN);
        }

        synchronized (this) {
            users.put(user.getName(), user);
        }

        return user.getName() + "@" + user.getDomain();
    }

    @Override
    public User getUser(String name, String pwd) throws MessagesException
    {
        User user;

        synchronized (this) {
            user = users.get(name);
        }

        if (user == null || !user.getPwd().equals(pwd)) {
        	throw new MessagesException(Status.FORBIDDEN);

        } else {
            return user;
        }

    }

    @Override
    public User updateUser(String name, String pwd, User user) throws MessagesException
    {

        User chk;

        synchronized (this) {
            chk = users.get(name);
        }

        if (chk == null || !chk.getPwd().equals(pwd)) {
        	throw new MessagesException(Status.FORBIDDEN);
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
    public String checkIfUserExists(String user) throws MessagesException
    {
        User chk = null;
        synchronized (this)
        {
            chk = users.get(user);
        }

        if (chk != null) {
            return chk.getDisplayName();
        } else
        	throw new MessagesException(Status.NOT_FOUND);

    }

    @Override
    public User deleteUser(String user, String pwd) throws MessagesException
    {
        User uncheck;
        synchronized (this) {
            uncheck = users.get(user);
        }

        if (uncheck == null || !uncheck.getPwd().equals(pwd)) {
        	throw new MessagesException(Status.FORBIDDEN);
        }

        try
        {
        	new ClientUtilsMessages(getURI(domain, MessageServiceSoap.NAME).toString()).deleteUserInbox(user);
        }
        catch (MalformedURLException | WebServiceException clientEx)
        {
        	throw new MessagesException(clientEx.getMessage());
        }

        synchronized (this) {
            uncheck = users.remove(user);
        }
        return uncheck;

    }

    private URI getURI(String domain, String serviceType)
    {
    	return discovery.getURI(domain, serviceType, discovery.WS_SOAP);
    }


}
