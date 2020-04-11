package sd1920.trab1.core.servers;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import javax.xml.ws.Endpoint;

import com.sun.net.httpserver.HttpServer;

import sd1920.trab1.core.resources.soap.*;
import sd1920.trab1.api.soap.MessageServiceSoap;
import sd1920.trab1.api.soap.UserServiceSoap;
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
	
	public static void main(String[] args) throws Exception
	{
		//ExecutorService messagePool = Executors.newFixedThreadPool(100);
		//ExecutorService usersPool = Executors.newFixedThreadPool(100);
		
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
		
		Endpoint soapUsersEndpoint = Endpoint.create(new UsersResource(discovery, domain));
		soapUsersEndpoint.publish(server.createContext(UserServiceSoap.PATH));
		Log.info("\nUsers Endpoint Created & Published");
		
		Thread.sleep(100);

		Endpoint soapMessagesEndpoint = Endpoint.create(new MessageResource(discovery, domain));
		soapMessagesEndpoint.publish(server.createContext(MessageServiceSoap.PATH));
		Log.info("\nMessage Endpoint Created & Published");
	
		Thread.sleep(100);
		
		server.start();
		
		Log.info(String.format("\n%s Server ready @ %s\n", MESSAGE_SERVICE, serverURI));
		Log.info(String.format("\n%s Server ready @ %s\n", USER_SERVICE, serverURI));
	}

}
