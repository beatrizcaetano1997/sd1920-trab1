package sd1920.trab1.core.servers;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import javax.xml.ws.Endpoint;

import com.sun.net.httpserver.HttpServer;

import sd1920.trab1.core.resources.soap.*;
import sd1920.trab1.api.soap.MessageService;
import sd1920.trab1.api.soap.UserService;
import sd1920.trab1.core.servers.discovery.Discovery;

public class DomainServerSOAP {

	private static Logger Log = Logger.getLogger(DomainServerSOAP.class.getName());

	static
	{
		System.setProperty("java.net.preferIPv4Stack", "true");
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
	}
	
	public static final int PORT = 8080;
	public static final String MESSAGE_SERVICE = "MessageService";
	public static final String USER_SERVICE = "UserService";

	//public static final String SOAP_MESSAGES_PATH = "/soap/messages"; //THIS IS TO BE PUT IN API.SOAP/MESSAGE&USERService
	
	public static void main(String[] args) throws Exception
	{
		//ExecutorService messagePool = Executors.newFixedThreadPool(15);
		//ExecutorService usersPool = Executors.newFixedThreadPool(15);
		
		String ip = InetAddress.getLocalHost().getHostAddress();
		//para correr sem ser no docker, mudar a string para "fct" ou "fcsh" por exemplo
		String domain = InetAddress.getLocalHost().getHostName();	
		String serverURI = String.format("http://%s:%s/soap", ip, PORT);
		
		Discovery discovery = new Discovery(new InetSocketAddress("226.226.226.226", 2266), domain, serverURI);
		discovery.start();
		
		// Create an HTTP server, accepting requests at PORT (from all local interfaces)
		HttpServer server = HttpServer.create(new InetSocketAddress(ip, PORT), 0);
		Log.info("\n SERVER AT: " + ip + ":" + PORT + ".\n");
		server.setExecutor(Executors.newCachedThreadPool());
		
		// Create a SOAP Endpoint (you need one for each service)
		// One thread per each endpoint
		//TODO: SEE HOW TO PUT EACH ENDPOINT IN THREAD
		
		/*
		messagePool.execute(new Thread( () -> {
			Endpoint soapMessagesEndpoint = Endpoint.create(new MessageResource(messageDiscovery, domain));
			soapMessagesEndpoint.publish(server.createContext(MessageService.PATH));
			Log.info("\nMessage Endpoint Created & Published");
		}));
		*/
		
		/*
		usersPool.execute(new Thread( () -> {
			Endpoint soapUsersEndpoint = Endpoint.create(new UsersResource(userDiscovery, domain));
			soapUsersEndpoint.publish(server.createContext(UserService.PATH));
			Log.info("\nUsers Endpoint Created & Published");
		}));
		*/
		
		Endpoint soapUsersEndpoint = Endpoint.create(new UsersResource(discovery, domain));
		soapUsersEndpoint.publish(server.createContext(UserService.PATH));
		Log.info("\nUsers Endpoint Created & Published\n");
		
		server.start();
		
		Log.info(String.format("\n%s Server ready @ %s\n", MESSAGE_SERVICE, serverURI));
		Log.info(String.format("\n%s Server ready @ %s\n", USER_SERVICE, serverURI));
	}

}
