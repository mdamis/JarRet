package upem.jarret.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Scanner;
import java.util.Set;

public class Server {
	private static final Charset charsetASCII = Charset.forName("ASCII");
	private static final Charset charsetUTF8 = Charset.forName("utf-8");
	private static final String badRequest = "HTTP/1.1 400 Bad Request\r\n\r\n";
	
	private final ServerSocketChannel ssc;
	private final Selector selector;
	private final Set<SelectionKey> selectedKeys;

	private final Thread consoleThread = new Thread(() -> {
		try (Scanner scanner = new Scanner(System.in)) {
			while (scanner.hasNextLine()) {
				switch (scanner.nextLine()) {
				case "SHUTDOWN":
					shutdown();
					return;
				case "SHUTDOWN NOW":
					shutdownNow();
					return;
				case "INFO":
					info();
					break;
				default:
					System.out.println("WRONG COMMAND");
					break;
				}
			}
		}
	});

	public Server(int port) throws IOException {
		ssc = ServerSocketChannel.open();
		ssc.bind(new InetSocketAddress(port));
		selector = Selector.open();
		selectedKeys = selector.selectedKeys();
	}

	private void info() {
		System.out.println("INFO");
		// TODO
	}

	private void shutdown() {
		System.out.println("SHUTDOWN");
		try {
			ssc.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void shutdownNow() {
		System.out.println("SHUTDOWN NOW");
		try {
			ssc.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		for (SelectionKey key : selectedKeys) {
			try {
				close(key);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void launch() throws IOException {
		consoleThread.start();

		ssc.configureBlocking(false);
		ssc.register(selector, SelectionKey.OP_ACCEPT);
		Set<SelectionKey> selectedKeys = selector.selectedKeys();
		while (!Thread.interrupted()) {
			selector.select();
			processSelectedKeys();
			selectedKeys.clear();
		}
	}

	private void processSelectedKeys() throws IOException {
		for (SelectionKey key : selectedKeys) {
			if (key.isValid() && key.isAcceptable()) {
				try {
					doAccept(key);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (key.isValid() && key.isWritable()) {
				doWrite(key);
			}
			if (key.isValid() && key.isReadable()) {
				doRead(key);
			}
		}
	}

	private void doAccept(SelectionKey key) throws IOException {
		SocketChannel sc = ssc.accept();
		if (sc == null) {
			return;
		}
		sc.configureBlocking(false);
		sc.register(selector, SelectionKey.OP_READ, new Attachment());
		System.out.println("New connection from "+sc.getRemoteAddress());
	}

	private void doRead(SelectionKey key) throws IOException {
		SocketChannel sc = (SocketChannel) key.channel();
		Attachment attachment = (Attachment) key.attachment();
		ByteBuffer bb = attachment.getBb();
		
		bb.clear();
		sc.read(bb);
		bb.flip();
		
		try {
			parserequest(charsetASCII.decode(bb).toString());
		} catch (Exception e){
			sc.write(charsetUTF8.encode(badRequest));
			return;
		}
	}

	private void parserequest(String request) {
		String[] lines = request.split("\r\n");
		
		String[] token  = lines[0].split(" ");
		String cmd = token[0];
		String requested = token[1];
		String protocol = token[2];
		
		if(cmd.equals("GET") && requested.equals("Task") && protocol.equals("HTTP/1.1")) {
			parseGET(lines[1]);
		} else if(cmd.equals("POST") && requested.equals("Answer") && protocol.equals("HTTP/1.1")) {
			parsePOST();
		}
	}

	private void parsePOST() {
		// TODO Auto-generated method stub
		
	}

	private void parseGET(String string) {
		// TODO Auto-generated method stub
		
	}

	private void doWrite(SelectionKey key) {
		// TODO Auto-generated method stub

	}

	private void close(SelectionKey key) throws IOException {
		key.channel().close();
	}

	public static void main(String[] args) throws NumberFormatException, IOException {
		new Server(Integer.parseInt(args[0])).launch();
	}

}
