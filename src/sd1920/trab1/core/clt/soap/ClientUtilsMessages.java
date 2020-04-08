package sd1920.trab1.core.clt.soap;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;

import com.sun.xml.ws.client.BindingProviderProperties;

import sd1920.trab1.api.Message;
import sd1920.trab1.api.User;
import sd1920.trab1.api.soap.MessageService;
import sd1920.trab1.api.soap.MessagesException;
import sd1920.trab1.api.soap.UsersException;

public class ClientUtilsMessages implements IClientUtilsMessages
{

	private static final int CONNECTION_TIMEOUT = 10000;
    private static final int REPLY_TIMOUT = 600;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_PERIOD = 1000;
    
    //TODO_TEST
    //private static final String MESSAGES_WSDL = "/messages?wsdl";
    private static final String MESSAGES_WSDL = "?wsdl";
    
    QName QNAME;
    Service service;
    MessageService messages;
	
	public ClientUtilsMessages(String serverURI) throws MalformedURLException, WebServiceException
	{
		QNAME = new QName(MessageService.NAMESPACE, MessageService.NAME);
		Service service = Service.create(new URL(serverURI + MESSAGES_WSDL), QNAME);
		messages = service.getPort( sd1920.trab1.api.soap.MessageService.class );
		
		((BindingProvider) messages).getRequestContext().put(BindingProviderProperties.CONNECT_TIMEOUT, CONNECTION_TIMEOUT);
		((BindingProvider) messages).getRequestContext().put(BindingProviderProperties.REQUEST_TIMEOUT, REPLY_TIMOUT);
	}

	@Override
	public Long postOtherDomainMessage(Message message, String user)
	{
		boolean success = false;
        short retries = 0;
        Long receivedId = 0L;

        while (!success && retries < MAX_RETRIES) {
            try
            {
                receivedId = messages.postOtherMessageDomain(message, user);
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

	@Override
	public boolean deleteOtherDomainMessage(String user, Message m)
	{
		boolean success = false;
        short retries = 0;

        while (!success && retries < MAX_RETRIES) {
            try
            {
                messages.deleteMessageFromOtherDomain(user, m);
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
        return success;
	}

	@Override
	public boolean deleteUserInbox(String user)
	{
		boolean success = false;
        short retries = 0;

        while (!success && retries < MAX_RETRIES) {
            try
            {
                messages.deleteUserInbox(user);
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
        return success;
	}
}
