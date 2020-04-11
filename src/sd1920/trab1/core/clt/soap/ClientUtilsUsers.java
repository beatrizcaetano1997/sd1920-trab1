package sd1920.trab1.core.clt.soap;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;

import com.sun.xml.ws.client.BindingProviderProperties;

import sd1920.trab1.api.Message;
import sd1920.trab1.api.User;
import sd1920.trab1.api.soap.MessagesException;
import sd1920.trab1.api.soap.MessageServiceSoap;
import sd1920.trab1.api.soap.UserServiceSoap;
import sd1920.trab1.core.clt.rest.ClientUtilsInterface;

public class ClientUtilsUsers implements IClientUtilsUsers
{
	private static final int CONNECTION_TIMEOUT = 10000;
    private static final int REPLY_TIMOUT = 600;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_PERIOD = 1000;
    
    private static final String USERS_WSDL = "/users/?wsdl";
    
    QName QNAME;
    Service service;
    UserServiceSoap users;
	
	public ClientUtilsUsers(String serverURI) throws MalformedURLException, WebServiceException
	{
		QNAME = new QName(UserServiceSoap.NAMESPACE, UserServiceSoap.NAME);
		Service service = Service.create(new URL(serverURI + USERS_WSDL), QNAME);
		users = service.getPort( sd1920.trab1.api.soap.UserServiceSoap.class );
		
		((BindingProvider) users).getRequestContext().put(BindingProviderProperties.CONNECT_TIMEOUT, CONNECTION_TIMEOUT);
		((BindingProvider) users).getRequestContext().put(BindingProviderProperties.REQUEST_TIMEOUT, REPLY_TIMOUT);
	}

	@Override
	public User checkUser(String user, String pwd)
	{
		boolean success = false;
        short retries = 0;
        User receivedUser = null;

        while (!success && retries < MAX_RETRIES) {
            try
            {
                receivedUser = users.getUser(user, pwd);
                success = true;
            }
            catch (MessagesException ex)
            {
            	success = true;
            }
            catch (WebServiceException wse )
            {
                retries++;

                try
                {
                    Thread.sleep(RETRY_PERIOD);
                }
                catch (InterruptedException ignored)
                {
                    //nothing to be done here, if it happens, it will retry sooner
                }
            }
        }
        return receivedUser;
	}

	@Override
	public String userExists(String user)
	{
		boolean success = false;
        short retries = 0;
        String receivedId = null;

        while (!success && retries < MAX_RETRIES) {
            try
            {
                receivedId = users.checkIfUserExists(user);
                success = true;
            }
            catch (MessagesException ex)
            {
            	success = true;
            }
            catch (WebServiceException wse )
            {
                retries++;

                try
                {
                    Thread.sleep(RETRY_PERIOD);
                }
                catch (InterruptedException ignored)
                {
                    //nothing to be done here, if it happens, it will retry sooner
                }
            }
        }
        return receivedId;
	}
}
