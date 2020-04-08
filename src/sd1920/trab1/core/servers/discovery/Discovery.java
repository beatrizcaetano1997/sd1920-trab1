package sd1920.trab1.core.servers.discovery;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * <p>A class to perform service discovery, based on periodic service contact endpoint
 * announcements over multicast communication.</p>
 *
 * <p>Servers announce their *name* and contact *uri* at regular intervals. The server actively
 * collects received announcements.</p>
 *
 * <p>Service announcements have the following format:</p>
 *
 * <p>&lt;service-name-string&gt;&lt;delimiter-char&gt;&lt;service-uri-string&gt;</p>
 */
public class Discovery {
    private static Logger Log = Logger.getLogger(Discovery.class.getName());

    static {
        // addresses some multicast issues on some TCP/IP stacks
        System.setProperty("java.net.preferIPv4Stack", "true");
        // summarizes the logging format
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
    }


    // The pre-aggreed multicast endpoint assigned to perform discovery.
    static final InetSocketAddress DISCOVERY_ADDR = new InetSocketAddress("226.226.226.226", 2266);
    static final int DISCOVERY_PERIOD = 1000;
    static final int DISCOVERY_TIMEOUT = 5000;

    // Used separate the two fields that make up a service announcement.
    private static final String DELIMITER = "\t";

    private InetSocketAddress addr;
    private String serviceName;
    private String serviceURI;
    private Map<String, URI> uriUsersMap = new HashMap<>();
    private Map<String, URI> uriMessagesMap = new HashMap<>();


    /**
     * @param serviceName the name of the service to announce
     * @param serviceURI  an uri string - representing the contact endpoint of the service being announced
     */
    //O Service name vai passar a ser o dominio
    public Discovery(InetSocketAddress addr, String serviceName, String serviceURI) {
        this.addr = addr;
        this.serviceName = serviceName;
        this.serviceURI = serviceURI;
    }

    public Discovery(InetSocketAddress addr, String serviceName) {
        this.addr = addr;
        this.serviceName = serviceName;
    }

    /**
     * Starts sending service announcements at regular intervals...
     */
    public void start() {
        Log.info(String.format("Starting Discovery announcements on: %s for: %s -> %s\n", addr, serviceName, serviceURI));

        byte[] announceBytes = String.format("%s%s%s", serviceName, DELIMITER, serviceURI).getBytes();
        DatagramPacket announcePkt = new DatagramPacket(announceBytes, announceBytes.length, addr);

        try {
            MulticastSocket ms = new MulticastSocket(addr.getPort());
            ms.joinGroup(addr.getAddress());
            ms.setSoTimeout(DISCOVERY_TIMEOUT);
            // start thread to send periodic announcements
            new Thread(() -> {
                for (; ; ) {
                    try {
                        ms.send(announcePkt);
                        Thread.sleep(DISCOVERY_PERIOD);
                    } catch (Exception e) {
                        e.printStackTrace();
                        // do nothing
                    }
                }
            }).start();

            // start thread to collect announcements
            new Thread(() -> {
                DatagramPacket pkt = new DatagramPacket(new byte[1024], 1024);
                for (; ; ) {
                    try {
                        long start = System.currentTimeMillis();
                        pkt.setLength(1024);
                        ms.receive(pkt);
                        String msg = new String(pkt.getData(), 0, pkt.getLength());
                        String[] msgElems = msg.split(DELIMITER);
                        URI uriService = URI.create(msgElems[1]);

                        if (msgElems.length == 2) {    //periodic announcement
//                            System.out.printf("FROM %s (%s) : %s\n",
//                                    pkt.getAddress().getCanonicalHostName(),
//                                    pkt.getAddress().getHostAddress(), msg);
                            if (uriService.toString().contains("users")) {
                                uriUsersMap.put(msgElems[0], uriService);
                            } else {
                                uriMessagesMap.put(msgElems[0], uriService);
                            }

                        }
                    } catch (SocketTimeoutException e) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignore) {
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startClient() {
        try (MulticastSocket ms = new MulticastSocket(addr.getPort())) {
            ms.joinGroup(addr.getAddress());

            // start thread to collect announcements
            new Thread(() -> {
                DatagramPacket pkt = new DatagramPacket(new byte[1024], 1024);
                for (; ; ) {
                    try {
                        long start = System.currentTimeMillis();
                        pkt.setLength(1024);
                        ms.receive(pkt);
                        String msg = new String(pkt.getData(), 0, pkt.getLength());
                        String[] msgElems = msg.split(DELIMITER);
                        URI uriService = URI.create(msgElems[1]);

                        if (msgElems.length == 2) {    //periodic announcement
//                            System.out.printf("FROM %s (%s) : %s\n",
//                                    pkt.getAddress().getCanonicalHostName(),
//                                    pkt.getAddress().getHostAddress(), msg);
                            if (uriService.toString().contains("users")) {
                                uriUsersMap.put(msgElems[0], uriService);
                            } else {
                                uriMessagesMap.put(msgElems[0], uriService);
                            }

                        }
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Returns the known servers for a service.
     *
     * @param serviceName the name of the service being discovered
     * @return an array of URI with the service instances discovered.
     */
    public URI[] knownUrisOf(String serviceName) {

        Set<URI> discoveredUris = new HashSet<URI>();

        for (String key : uriUsersMap.keySet()) {
            if (key.equals(serviceName)) {
                discoveredUris.add(uriUsersMap.get(key));
            }
        }

        for (String key : uriMessagesMap.keySet()) {
            if (key.equals(serviceName)) {
                discoveredUris.add(uriMessagesMap.get(key));
            }
        }

        return discoveredUris.toArray(new URI[discoveredUris.size()]);
    }

    // Main just for testing purposes
    public static void main(String[] args) throws Exception {
    }
}
