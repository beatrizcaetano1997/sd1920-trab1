package sd1920.trab1.core.resources.soap;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.*;
import java.util.logging.Logger;

import javax.jws.WebService;
import javax.ws.rs.*;

import javax.ws.rs.core.Response.Status;
import javax.xml.ws.WebServiceException;

import sd1920.trab1.api.*;
import sd1920.trab1.api.rest.MessageService;
import sd1920.trab1.api.soap.*;
import sd1920.trab1.core.clt.rest.ClientUtils;
import sd1920.trab1.core.clt.soap.*;
import sd1920.trab1.core.servers.discovery.Discovery;

@WebService(serviceName=MessageServiceSoap.NAME, 
targetNamespace=MessageServiceSoap.NAMESPACE, 
endpointInterface=MessageServiceSoap.INTERFACE)
public class MessageResource implements MessageServiceSoap {

    private Random randomNumberGenerator;

    private final HashMap<Long, Message> allMessages = new HashMap<>();
    private final HashMap<String, Set<Long>> userInboxs = new HashMap<>();

    private static Logger Log = Logger.getLogger(MessageResource.class.getName());
    private Discovery discovery;
    private String domain;

    public MessageResource(Discovery discovery, String domain) {
        this.randomNumberGenerator = new Random(System.currentTimeMillis());
        this.discovery = discovery;
        this.domain = domain;
    }

    @Override
    public long postMessage(String pwd, Message msg) throws MessagesException
    {
        //Check if message is valid, if not return HTTP CONFLICT (409)
        if (msg.getSender() == null || msg.getDestination() == null || msg.getDestination().size() == 0) {
        	throw new MessagesException(Status.CONFLICT);
        }


        User userExists = null;
		try
		{
			userExists = new ClientUtilsUsers(getURI(domain, UserServiceSoap.NAME))
												   .checkUser(msg.getSender().split("@")[0], pwd);
		}
		catch (MalformedURLException | WebServiceException e)
		{
			throw new MessagesException(e.getMessage());
		}

        //Check if a user is valid
        if (userExists == null) {
        	throw new MessagesException(Status.FORBIDDEN);
        }

        long newID = Math.abs(randomNumberGenerator.nextLong());
        synchronized (this) {
            while (allMessages.containsKey(newID)) {
                newID = Math.abs(randomNumberGenerator.nextLong());
            }
        }

        msg.setId(newID);
//            Add the message to the global list of messages
        String senderFormated = userExists.getDisplayName() + " <" + userExists.getName() + "@" + domain + ">";

        //I just created this string to not having to split again when processing the failed message
        String notformatedSender = msg.getSender();

        msg.setSender(senderFormated);
        synchronized (this) {
            allMessages.put(newID, msg);
        }

        Log.info("Created new message with id: " + newID);

        //Add the message (identifier) to the inbox of each recipient
        for (String recipient : msg.getDestination()) {
            String userDomain = recipient.split("@")[1];
            if (!userDomain.equals(domain)) {
                //Makes sure that the destination is only 1 and not all, to avoid server loops

                long midFromOtherDomain = -1;
                Message toSend = getMessage(msg);
                
                String uri = getURI(recipient.split("@")[1], MessageServiceSoap.NAME);
                if (uri.contains(discovery.WS_REST))
                {
                	midFromOtherDomain = new ClientUtils(uri)
       					 .postOtherDomainMessage(toSend, recipient);
                }
                else if (uri.contains(discovery.WS_SOAP))
                {
                	try
                    {
    					midFromOtherDomain = new ClientUtilsMessages(uri)
    											.postOtherDomainMessage(toSend, recipient);
    				}
                    catch (MalformedURLException | WebServiceException e)
                    {
    					throw new MessagesException(e.getMessage());
    				}
                }
                

                //verificação se a mensagem foi realmente enviada
                if (midFromOtherDomain == -1)
                {
                    //Puts failed sent message in sender inbox
                    userFailedMessage(msg, recipient, notformatedSender);
                }

            }
            else
            {
            	String chk = null;
				try
				{
					chk = new ClientUtilsUsers(getURI(domain, UserServiceSoap.NAME)).userExists(recipient.split("@")[0]);
				}
				catch (MalformedURLException | WebServiceException e)
				{
					throw new MessagesException(e.getMessage());
				}
            	
                if (chk != null) {

                    Set<Long> msgSet;
                    synchronized (userInboxs) {
                        msgSet = userInboxs.get(recipient);
                    }

                    if (msgSet == null) {
                        synchronized (userInboxs) {
                            userInboxs.put(recipient, new HashSet<>());
                        }
                    }

                    synchronized (userInboxs) {
                        userInboxs.get(recipient).add(newID);
                    }

                } else {
                    //Puts failed sent message in sender inbox
                    userFailedMessage(msg, recipient, notformatedSender);
                }
            }
        }

        Log.info("Recorded message with identifier: " + newID);
        return newID;
    }


    @Override
    public Message getMessage(String user, String pwd, long mid) throws MessagesException
    {
        Log.info("Received request for message with id: " + mid + ".");

        User userExists = null;
        try
		{
			userExists = new ClientUtilsUsers(getURI(domain, UserServiceSoap.NAME))
							 .checkUser(user, pwd);
		}
		catch (MalformedURLException | WebServiceException e)
		{
			throw new MessagesException(e.getMessage());
		}

        //Check if a user is valid
        if (userExists == null)
        {
        	throw new MessagesException(Status.FORBIDDEN);
        }

        Message m;
        String key = user + "@" + domain;
        Set<Long> userSet;
        synchronized (userInboxs) {
            userSet = userInboxs.get(key);
        }

        if (!userSet.contains(mid)) {
        	throw new MessagesException(Status.NOT_FOUND); //if not send HTTP 404 back to client
        }

        synchronized (this) {
            m = allMessages.get(mid);
        }

        Log.info("Returning requested message to user.");

        return m; //Return message to the client with code HTTP 200

    }


    @Override
    public List<Long> getMessages(String user, String pwd) throws MessagesException
    {
        Log.info("Received request for messages with optional user parameter set to: '" + user + "'");

        List<Long> messages = new LinkedList<>();

        User userExists = null;
        try
		{
			userExists = new ClientUtilsUsers(getURI(domain, UserServiceSoap.NAME))
							 .checkUser(user, pwd);
		}
		catch (MalformedURLException | WebServiceException e)
		{
			throw new MessagesException(e.getMessage());
		}
        
        if (userExists == null) {
        	throw new MessagesException(Status.FORBIDDEN);
        }

        Log.info("Collecting all messages in server for user " + user);

        Set<Long> mids;
        String key = user + "@" + domain;

        synchronized (userInboxs) {
            mids = userInboxs.getOrDefault(key, Collections.emptySet());
        }

        for (Long l : mids) {
            Log.info("Adding message with id: " + l + ".");
            messages.add(l);
        }


        Log.info("Returning message list to user with " + messages.size() + " messages.");
        return messages;
    }

    @Override
    public void removeFromUserInbox(String user, String pwd, long mid) throws MessagesException
    {
    	User userExists = null;
        try
		{
			userExists = new ClientUtilsUsers(getURI(domain, UserServiceSoap.NAME))
							 .checkUser(user, pwd);
		}
		catch (MalformedURLException | WebServiceException e)
		{
			throw new MessagesException(e.getMessage());
		}
        
        //Check if a user is valid
        if (userExists == null) {
        	throw new MessagesException(Status.FORBIDDEN);
        }

        String key = user + "@" + domain;
        Set<Long> userSet;
        synchronized (userInboxs) {
            userSet = userInboxs.get(key);
        }

        if (!userSet.contains(mid)) {
        	throw new MessagesException(Status.NOT_FOUND); //if not send HTTP 404 back to client
        }

        synchronized (userInboxs) {
            userInboxs.get(key).remove(mid);
        }
    }

    @Override
    public void deleteMessage(String user, String pwd, long mid) throws MessagesException
    {

    	User userExists = null;
        try
		{
			userExists = new ClientUtilsUsers(getURI(domain, UserServiceSoap.NAME))
							 .checkUser(user, pwd);
		}
		catch (MalformedURLException | WebServiceException e)
		{
			throw new MessagesException(e.getMessage());
		}

        //Check if a user is valid
        if (userExists == null) {
        	throw new MessagesException(Status.FORBIDDEN);
        }


        Message msg;
        synchronized (this) {
            msg = allMessages.get(mid);
        }
        if (msg != null && msg.getSender().contains(user)) {
            synchronized (this) {
                msg = allMessages.remove(mid);
            }
        } else {
            return;
        }

        Set<String> msgDestination = msg.getDestination();

        for (String user_dest : msgDestination) {
            if (!user_dest.split("@")[1].equals(domain))
            {
                String uri = getURI(user_dest.split("@")[1], MessageServiceSoap.NAME);
                	
                if (uri.contains(discovery.WS_REST))
                   	new ClientUtils(uri).deleteOtherDomainMessage(user_dest, msg);
                else if (uri.contains(discovery.WS_SOAP))
                {
                   	try
                  	{
    					new ClientUtilsMessages(uri).deleteOtherDomainMessage(user_dest, msg);
    				}
                   	catch (MalformedURLException | WebServiceException e)
                   	{
    					throw new WebApplicationException(e.getMessage());
    				}
                }
            }
            else
            {
                synchronized (userInboxs) {
                    userInboxs.get(user_dest).remove(mid);
                }
            }
        }
    }

    @Override
    public long postOtherMessageDomain(Message m, String user) throws MessagesException
    {
    	String chk = null;
		try
		{
			chk = new ClientUtilsUsers(getURI(domain, UserServiceSoap.NAME)).userExists(user.split("@")[0]);
		}
		catch (MalformedURLException | WebServiceException e)
		{
			throw new MessagesException(e.getMessage());
		}
		
        if (chk != null) {
            long newID = m.getId();

            Message tocheck;
            synchronized (this) {
                tocheck = allMessages.get(m.getId());
            }

            if (tocheck == null) {
                synchronized (this) {
                    allMessages.put(m.getId(), m);
                }
            }

            Set<Long> userSet;
            synchronized (userInboxs) {
                userSet = userInboxs.get(user);
            }

            if (userSet == null) {
                synchronized (userInboxs) {
                    userInboxs.put(user, new HashSet<>());
                }
            }
            synchronized (userInboxs) {
                userInboxs.get(user).add(newID);
            }

            return newID;
        } else {
        	throw new MessagesException(Status.NOT_FOUND);
        }
    }


    //method to delete messages from other domains
    @Override
    public void deleteMessageFromOtherDomain(String user, Message m) {
        synchronized (this) {
            allMessages.remove(m.getId());
        }
        synchronized (userInboxs) {
            userInboxs.get(user).remove(m.getId());
        }
    }

    @Override
    public void deleteUserInbox(String user) {
        synchronized (userInboxs) {
            userInboxs.remove(user);
        }
    }

    //PRIVATE UTIL METHODS
    private void userFailedMessage(Message msg, String recipient, String notFormatedSender) {
        Message toUserMessage = new Message();

        long newFailedMessageID = Math.abs(randomNumberGenerator.nextLong());
        synchronized (this) {
            while (allMessages.containsKey(newFailedMessageID)) {
                newFailedMessageID = Math.abs(randomNumberGenerator.nextLong());
            }
        }


        toUserMessage.setId(newFailedMessageID);
        toUserMessage.setSender(msg.getSender());
        toUserMessage.setDestination(msg.getDestination());
        toUserMessage.setContents(msg.getContents());
        toUserMessage.setSubject("FALHA NO ENVIO DE " + msg.getId() + " PARA " + recipient);

        synchronized (this) {
            allMessages.put(newFailedMessageID, toUserMessage);
        }
        synchronized (this) {
            userInboxs.get(notFormatedSender).add(newFailedMessageID);
        }
    }

    private Message getMessage(Message msg) {
        Message toSend = new Message(msg.getSender(), msg.getDestination(), msg.getSubject(), msg.getContents());
        toSend.setCreationTime(msg.getCreationTime());
        toSend.setId(msg.getId());
        return toSend;
    }

    public void deleteFailedMessage(long mid, String user, String recipient) {
        synchronized (this) {
            for (long mids : userInboxs.get(user)) {
                if (allMessages.get(mids).getSubject().equals("FALHA NO ENVIO DE " + mid + " " + recipient)) {
                    allMessages.remove(mids);
                    userInboxs.get(user).remove(mids);
                }
            }
        }
    }
    
    private String getURI(String domain, String serviceType)
    {
    	return discovery.getURI(domain, serviceType).toString();
    }
}
