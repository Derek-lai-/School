/**
 * HTTP Server, NIO (channelized, asynchronous) version.
 * Usage:  java NIOHTTPServer [port_#  [#_threads [http_root_path ] ] ]

 * NIOHTTPServer is a non-blocking (NIO) server that uses SocketChannels
 * and a pool of threads to service requests.  Note that SocketChannels
 * are thread safe, that is, multiple threads don't have to synchronize
 * concurrent access since only one read or write operation is in progress
 * at any given time.  However some other parts of the implementation are
 * not thread safe and require use of synchronized methods.
 *
 * A single thread executes the main loop that repeatedly sleeps and 
 * wakes to check for events such as connection attempts, reads and writes.
 *
 * Whenever an event needs attention, the main thread places the event
 * in a synchronized ClientQueue, from which a thread from the thread pool
 * will retrieve and service it (handle a read or write request).
 */

import java.io.*;
import java.util.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.regex.*;

public class NIOHTTPServer {
  Selector clientSelector;
  String http_root;
  ClientQueue readyClients = new ClientQueue();

  public void initialize(int port, int threads, String http_root) throws IOException {
      clientSelector = Selector.open();
      this.http_root = http_root;
      ServerSocketChannel channel = ServerSocketChannel.open();
      channel.configureBlocking(false);
      InetSocketAddress socketAddr = 
	  // Use this version if your server is NOT running on mathlab
	  // and change fissure.utsc.utoronto.ca to your actual server host.
	  // You can also use this version if your server is mathlab (change
	  // fissure obviously) and your client is not running on mathlab. 
          new InetSocketAddress(InetAddress.getByName("cms-chorus.utsc.utoronto.ca"), port);
	  // Because mathlab.utsc.utoronto.ca has multiple interfaces
	  // it can return the wrong interface in response to getLocalHost()
          // Use this version if both server and client are on mathlab,
          // changing your http_load URL's to use "localhost" rather than
          // "mathlab.utsc.utoronto.ca"
          // new InetSocketAddress(InetAddress.getByName("localhost"), port);
      channel.socket().bind(socketAddr);
      channel.register(clientSelector, SelectionKey.OP_ACCEPT);
  
      for (int i=0; i<threads; i++)
          new Thread() { public void run() { 
             while (true) 
	         try { handleClientRequest(); }
	         catch (IOException e) { } 
          } }.start();

      while (true) try {
          while (clientSelector.select(50) == 0);

          Set ready = clientSelector.selectedKeys();

          for (Iterator <SelectionKey>i = ready.iterator(); i.hasNext();) {
              SelectionKey key = (SelectionKey)i.next();
              i.remove();

              if (key.isAcceptable()) 
                  acceptConnection(channel);
              else {
                  key.interestOps(0);
                  readyClients.add(key);
              }
          }
      } catch (IOException e) { System.out.println(e); }
  }

  void acceptConnection(ServerSocketChannel channel) throws IOException {
      SocketChannel clientSkt = channel.accept();
      //http_load_off  System.out.println("received request from " + clientSkt);
      clientSkt.configureBlocking(false);
      SelectionKey key = clientSkt.register(clientSelector, SelectionKey.OP_READ);
      HTTPConnection client = new HTTPConnection(clientSkt, http_root);
      key.attach(client);
  }

  void handleClientRequest() throws IOException {
      SelectionKey key = readyClients.next();
      HTTPConnection client = (HTTPConnection)key.attachment();
      if (key.isReadable())
          client.read(key);
      else 
          client.write(key);
      clientSelector.wakeup();
  }

  public static void main(String args[]) throws IOException {
      int port = 9999;  // change this to use one of your assigned ports
      int threads = 4;   // level of multi-threading is adjustable
      String http_root = "./";  // http document root, default is local dir
      // allow user to choose a port, as arg[0]
      if (args.length >= 1)
          port = Integer.parseInt(args[0]);
      // allow user to choose number of threads, as arg[1]
      if (args.length >= 2)
          threads = Integer.parseInt(args[1]);
      // allow user to set http_root, as arg[2] 
      if (args.length >= 3)
          http_root = args[2];

      // display error on server stdout if usage is incorrect
      if (args.length > 3) {
          System.out.println("usage: java HTTPServer [port_# [threads [root_path] ] ]");
	  System.exit(0);
      }

      System.out.print("NIOHTTPServer listening on port: " + port);
      System.out.println(" using " + threads + " threads with server root path: " + http_root);
      new NIOHTTPServer().initialize(port, threads, http_root);
  }
}

class HTTPConnection {
  static Charset charSet = Charset.forName("8859_1");
  static Pattern GetRequestPattern = Pattern.compile("(?s)GET /?(\\S*).*");
  SocketChannel clientSkt;
  String http_root;
  ByteBuffer buff = ByteBuffer.allocateDirect(64*1024);
  String req;
  String resp = "HTTP/1.0 200 OK\n";    // Default response
  FileChannel file;
  int filePosn;

  HTTPConnection (SocketChannel clientSkt, String http_root) {
      this.clientSkt = clientSkt;
      this.http_root = http_root;
  }

  void read(SelectionKey key) throws IOException {
      if (req == null && (clientSkt.read(buff) == -1 
              || buff.get(buff.position()-1) == '\n'))
          processRequest(key);
      else
          key.interestOps(SelectionKey.OP_READ);
  }

  void processRequest(SelectionKey key) throws IOException {
      buff.flip();
      req = charSet.decode(buff).toString();
      //http_load_off  System.out.println("Request line: " + req);
      Matcher get = GetRequestPattern.matcher(req);
      if (get.matches()) {
          req = get.group(1);
	  req = http_root + req;
          if (req.endsWith("/") || req.equals(""))
              req = req + "index.html";
          try {
		 int length = 0;
                 file = new FileInputStream (req).getChannel();
		 if (file != null)
                    length = (int)file.size();
                 resp = resp + "Content-length: "
			 + Integer.toString(length) + "\n";
		 if (req.endsWith(".html") || req.endsWith(".htm"))
                     resp = resp + "Content-Type: text/html\n\n";
                 else if (req.endsWith(".jpg"))
                     resp = resp + "Content-Type: image/jpeg\n\n";
		 else
                     resp = resp + "Content-Type: text/plain\n\n";
           } catch (FileNotFoundException e) {
              resp = "HTTP/1.0 404 File Not Found";
           }
      } else
          resp = "HTTP/1.0 400 Bad Request" ;

      if (resp != null) {
          buff.clear();
          charSet.newEncoder().encode(CharBuffer.wrap(resp), buff, true);
          buff.flip();
      }
      key.interestOps(SelectionKey.OP_WRITE);
  }

  void write(SelectionKey key) throws IOException {
      if (resp != null) {
          clientSkt.write(buff);
          if (buff.remaining() == 0)
              resp = null;
      } 
      if (file != null) {
          int remaining = (int)file.size()-filePosn;
          long trans = file.transferTo(filePosn, remaining, clientSkt);
          if (trans == -1 || remaining <= 0) {
              file.close();
              file = null;
          } else
              filePosn += trans;
      } 
      if (resp == null && file == null) {
          clientSkt.close();
          key.cancel();        
      } else 
          key.interestOps(SelectionKey.OP_WRITE);
  }
}

class ClientQueue extends ArrayList {
  public static final long serialVersionUID = 1L;

  synchronized void add(SelectionKey key) { 
      super.add(key); 
      notify();
  }

  synchronized SelectionKey next() {
      while (isEmpty())
          try { wait(); }
	  catch (InterruptedException e) { }
      return (SelectionKey)remove(0);
  }
}