package upem.jarret.server;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import upem.jarret.http.HTTPReader;

public class Server {
	static final Charset charsetASCII = Charset.forName("ASCII");
	static final Charset charsetUTF8 = Charset.forName("utf-8");
	static final String badRequest = "HTTP/1.1 400 Bad Request\r\n\r\n";

	private final ServerSocketChannel ssc;
	private final Selector selector;
	private final Set<SelectionKey> selectedKeys;

	private final int port;
	private final String logPath;
	private final String answersPath;
	private final long maxFileSize;
	private final int comeBackInSeconds;

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

	private Server(int port, String logPath, String answersPath, long maxFileSize, int comeBackInSeconds) throws IOException {
		this.port = port;
		this.logPath = logPath;
		this.answersPath = answersPath;
		this.maxFileSize = maxFileSize;
		this.comeBackInSeconds = comeBackInSeconds;
		
		ssc = ServerSocketChannel.open();
		ssc.bind(new InetSocketAddress(port));
		selector = Selector.open();
		selectedKeys = selector.selectedKeys();
	}

	static boolean readFully(ByteBuffer bb, SocketChannel sc) throws IOException {
		while (sc.read(bb) != -1) {
			if (!bb.hasRemaining()) {
				return true;
			}
		}
		return false;
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
		consoleThread.setDaemon(true);
		consoleThread.start();

		ssc.configureBlocking(false);
		ssc.register(selector, SelectionKey.OP_ACCEPT);
		System.out.println("Server launched on port " + ssc.getLocalAddress());
		Set<SelectionKey> selectedKeys = selector.selectedKeys();
		while (!Thread.interrupted()) {
			selector.select();
			processSelectedKeys();
			selectedKeys.clear();
		}
	}

	private void processSelectedKeys() {
		for (SelectionKey key : selectedKeys) {
			if (key.isValid() && key.isAcceptable()) {
				try {
					doAccept(key);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (key.isValid() && key.isWritable()) {
				try {
					doWrite(key);
				} catch (IOException e) {
					key.cancel();
					System.out.println("Connection lost with client");
				}
			}
			if (key.isValid() && key.isReadable()) {
				try {
					doRead(key);
				} catch (IOException e) {
					key.cancel();
					System.out.println("Connection lost with client");
				}
			}
		}
	}

	private void doAccept(SelectionKey key) throws IOException {
		SocketChannel sc = ssc.accept();
		if (sc == null) {
			return;
		}
		sc.configureBlocking(false);
		sc.register(selector, SelectionKey.OP_READ, new Attachment(sc));
		System.out.println("New connection from " + sc.getRemoteAddress());
	}

	private void doRead(SelectionKey key) throws IOException {
		SocketChannel sc = (SocketChannel) key.channel();
		Attachment attachment = (Attachment) key.attachment();
		HTTPReader reader = attachment.getReader();

		String line = reader.readLineCRLF();

		try {
			parseRequest(line, attachment);
		} catch (Exception e) {
			sc.write(charsetUTF8.encode(badRequest));
			return;
		}

		key.interestOps(SelectionKey.OP_WRITE);
	}

	private void parseRequest(String request, Attachment attachment) throws IOException {
		System.out.println("request: " + request);
		String firstLine = request.split("\r\n")[0];
		String[] token = firstLine.split(" ");
		String cmd = token[0];
		String requested = token[1];
		String protocol = token[2];

		if (cmd.equals("GET") && requested.equals("Task") && protocol.equals("HTTP/1.1")) {
			attachment.requestTask();
		} else if (cmd.equals("POST") && requested.equals("Answer") && protocol.equals("HTTP/1.1")) {
			String answer = parsePOST(attachment);
			Objects.requireNonNull(answer);
			attachment.requestAnswer(answer);
		} else {
			throw new IllegalArgumentException();
		}
	}

	private String parsePOST(Attachment attachment) throws IOException {
		HTTPReader reader = attachment.getReader();
		String line;
		int contentLength = 0;
		while (!(line = reader.readLineCRLF()).equals("")) {
			String[] token = line.split(": ");
			if (token[0].equals("Content-Length")) {
				contentLength = Integer.parseInt(token[1]);
			}
			if (token[0].equals("Content-Type")) {
				if (!token[1].equals("application/json")) {
					throw new IllegalArgumentException();
				}
			}
		}
		ByteBuffer bb = reader.readBytes(contentLength);
		bb.flip();
		// something to do with these??
		long jobId = bb.getLong();
		int task = bb.getInt();
		String answer = charsetUTF8.decode(bb).toString();
		saveAnswer(jobId, task, answer);
		return answer; 
	}

	private void saveAnswer(long jobId, int task, String answer) {
		Path answerFilePath = Paths.get(answersPath + jobId + "_" + task);

		File answerFile = answerFilePath.toFile();
		try {
			if(answerFile.createNewFile()) {
				System.out.println(answerFilePath + " created");
			} else {
				System.out.println(answerFilePath + " found");
			}
		} catch (IOException e) {
			System.err.println("Error while trying to create a new File");
		}
		
		try(PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(answerFile, true)))) {
			out.println("=====\n" + answer + "\n=====");
		} catch (IOException e) {
	        System.err.println(e);
        }
	}
	
	private static Server create() throws JsonParseException, IOException {
		File serverConfig = Paths.get("config/JarRetConfig.json").toFile();
		
		int port = 8080;
		String logPath = "log/";
		String answersPath = "answers/";
		long maxFileSize = 0;
		int comeBackInSeconds = 300;
		
		JsonFactory jf = new JsonFactory();
		JsonParser jp = jf.createParser(serverConfig);
		jp.nextToken();
		while(jp.nextToken() != JsonToken.END_OBJECT) {
			String fieldName = jp.getCurrentName();
			System.out.println(fieldName);
			jp.nextToken();
			switch(fieldName) {
			case "Port":
				port = jp.getIntValue();
				break;
			case "LogDirectory":
				logPath = jp.getText();
				break;
			case "AnswersDirectory":
				answersPath = jp.getText();
				break;
			case "MaxFileSize":
				maxFileSize = jp.getLongValue();
				break;
			case "ComeBackInSeconds":
				comeBackInSeconds = jp.getIntValue();
				break;
			default:
				System.err.println("Unknown Field");
			}
		}
		
		return new Server(port, logPath, answersPath, maxFileSize, comeBackInSeconds);
		
	}

	private void doWrite(SelectionKey key) throws IOException {
		Attachment attachment = (Attachment) key.attachment();

		if (attachment.isRequestingTask()) {
			attachment.sendTask((SocketChannel) key.channel());
			System.out.println("task send");
			key.interestOps(SelectionKey.OP_READ);
		} else if (attachment.isSendingPost()) {
			attachment.sendCheckCode((SocketChannel) key.channel());
			key.interestOps(SelectionKey.OP_READ);
			// key.interestOps(0);
		}
	}

	private void close(SelectionKey key) throws IOException {
		key.channel().close();
	}

	public static void main(String[] args) throws NumberFormatException, IOException {
		Server.create().launch();
	}

}
