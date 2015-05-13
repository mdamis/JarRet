package upem.jarret.server;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;

import upem.jarret.http.HTTPReader;
import upem.jarret.job.Job;
import util.JsonTools;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class Server {
	private static final String HTTP_1_1_200_OK = "HTTP/1.1 200 OK\r\n\r\n";
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
	private final ArrayDeque<Job> jobs = new ArrayDeque<Job>();

	private boolean shutdown = false;
	private SelectionKey acceptKey;
	private int clients = 0;
	private int nbAnswers = 0;

	private final Thread consoleThread = new Thread(() -> {
		try (Scanner scanner = new Scanner(System.in)) {
			while (scanner.hasNextLine()) {
				switch (scanner.nextLine()) {
				case "SHUTDOWN":
					shutdown();
					break;
				case "SHUTDOWN NOW":
					shutdownNow();
					break;
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

	private Server(int port, String logPath, String answersPath, long maxFileSize, int comeBackInSeconds)
	        throws IOException {
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
		System.out.println("Connected clients: "+clients);
		System.out.println("Next task: - jobId: "+jobs.getFirst().getJobId()+" - task: "+jobs.getFirst().getCurrentTask());
		System.out.println("Answers received: "+nbAnswers);
	}

	private void shutdown() {
		System.out.println("SHUTDOWN");

		try {
			close(acceptKey);
		} catch (IOException e) {
			//
		}
		shutdown = true;
	}

	private void shutdownNow() {
		System.out.println("SHUTDOWN NOW");
		try {
			close(acceptKey);
		} catch (IOException e) {
			//
		}
		for (SelectionKey key : selector.keys()) {
			try {
				close(key);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		shutdown = true;
	}

	public void launch() throws IOException {
		consoleThread.setDaemon(true);
		consoleThread.start();

		ssc.configureBlocking(false);
		acceptKey = ssc.register(selector, SelectionKey.OP_ACCEPT);
		System.out.println("Server launched on port " + ssc.getLocalAddress());
		Set<SelectionKey> selectedKeys = selector.selectedKeys();

		loadJobs();
		
		while (!selector.keys().isEmpty() || !shutdown) {
			selector.select(300);
			processSelectedKeys();
			selectedKeys.clear();
		}
	}

	private void loadJobs() throws JsonParseException, IOException {
		Path jobsConfigPath = Paths.get("config/JarRetJobs.json");

		JsonFactory jf = new JsonFactory();
		JsonParser jp = jf.createParser(Files.newBufferedReader(jobsConfigPath));

		JsonToken current = jp.nextToken();
		while (current != null) {
			switch (current) {
			case START_OBJECT:
				Job job = Job.parseJSON(jp);
				for (int i = 0; i < Integer.parseInt(job.getJobPriority()); i++) {
					jobs.add(job);
				}
				break;
			default:
				break;
			}
			current = jp.nextToken();
		}

		for (Job job : jobs) {
			System.out.println(job);
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
				try {
					doWrite(key);
				} catch (IOException e) {
					close(key);
					System.out.println("Connection lost with client");
					clients--;
				}
			}
			if (key.isValid() && key.isReadable()) {
				try {
					doRead(key);
				} catch (IOException e) {
					close(key);
					System.out.println("Connection lost with client");
					clients--;
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
		clients++;
	}

	private void doRead(SelectionKey key) throws IOException {
		SocketChannel sc = (SocketChannel) key.channel();
		Attachment attachment = (Attachment) key.attachment();
		HTTPReader reader = attachment.getReader();

		String line = reader.readLineCRLF();

		try {
			parseRequest(line, attachment);
		} catch (Exception e) {
			//e.printStackTrace();
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
			while (!attachment.getReader().readLineCRLF().equals("")) { /* read useless parameters with the GET request */
			}
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
		if (answer != null && JsonTools.isJSON(answer)) {
			saveAnswer(jobId, task, answer);
			nbAnswers++;
		}

		return answer;
	}

	private void saveLog() {
		// TODO
	}

	private void saveAnswer(long jobId, int task, String answer) {
		Path answerFilePath = Paths.get(answersPath + jobId);

		try (BufferedWriter writer = Files.newBufferedWriter(answerFilePath, StandardOpenOption.APPEND,
		        StandardOpenOption.CREATE); PrintWriter out = new PrintWriter(writer)) {
			out.println(answer + '\n');
		} catch (IOException e) {
			System.err.println(e);
		}
	}

	private static Server create() throws JsonParseException, IOException {
		Path serverConfigPath = Paths.get("config/JarRetConfig.json");

		int port = 8080;
		String logPath = "log/";
		String answersPath = "answers/";
		long maxFileSize = 0;
		int comeBackInSeconds = 300;

		JsonFactory jf = new JsonFactory();
		JsonParser jp = jf.createParser(Files.newBufferedReader(serverConfigPath));
		jp.nextToken();
		while (jp.nextToken() != JsonToken.END_OBJECT) {
			String fieldName = jp.getCurrentName();
			jp.nextToken();
			switch (fieldName) {
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

	public void sendTask(SocketChannel sc) throws IOException {
		Job job = jobs.poll();
		ByteBuffer jsonBuffer;
		if (job == null) {
			jsonBuffer = charsetUTF8.encode("\"ComeBackInSeconds\":" + comeBackInSeconds);
		} else {
			while (job.isFinished()) {
				job = jobs.poll();
			}
			String json = job.nextTask().toJSON();
			System.out.println(json);
			jsonBuffer = Server.charsetUTF8.encode(json);
		}

		String header = "HTTP/1.1 200 OK\r\n" + "Content-Type: application/json; charset=utf-8\r\n"
		        + "Content-Length: " + jsonBuffer.remaining() + "\r\n\r\n";
		ByteBuffer headerBuffer = Server.charsetUTF8.encode(header);

		// System.out.println("Task: "+task);
		while (headerBuffer.hasRemaining()) {
			sc.write(headerBuffer);
		}

		while (jsonBuffer.hasRemaining()) {
			sc.write(jsonBuffer);
		}
		if (!job.isFinished()) {
			jobs.addLast(job);
		}
	}

	private void doWrite(SelectionKey key) throws IOException {
		Attachment attachment = (Attachment) key.attachment();

		if (attachment.isRequestingTask()) {
			attachment.setRequestingTask(false);
			sendTask((SocketChannel) key.channel());
			System.out.println("task send");
			key.interestOps(SelectionKey.OP_READ);
		} else if (attachment.isSendingPost()) {
			sendCheckCode(key);
			key.interestOps(SelectionKey.OP_READ);
			// key.interestOps(0);
		}
	}

	private void sendCheckCode(SelectionKey key) throws IOException {
		Attachment attachment = (Attachment) key.attachment();
		SocketChannel sc = (SocketChannel) key.channel();
		String answer = attachment.getAnswer();
		if (answer == null) {
			throw new IllegalArgumentException("No answer");
		}
		if (JsonTools.isJSON(answer)) {
			sc.write(Server.charsetUTF8.encode(HTTP_1_1_200_OK));
		} else {
			sc.write(Server.charsetUTF8.encode(badRequest));
		}

		attachment.clean(sc);
	}

	private void close(SelectionKey key) throws IOException {
		try{
			key.channel().close();
			key.cancel();
		} catch(Exception e) {
			//
		}
	}

	public static void main(String[] args) throws NumberFormatException, IOException {
		Server.create().launch();
	}

}
