package upem.jarret.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.rmi.UnexpectedException;

import upem.jarret.http.HTTPHeader;
import upem.jarret.http.HTTPReader;
import upem.jarret.worker.Worker;
import upem.jarret.worker.WorkerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class Client {
	private static final Charset charsetASCII = Charset.forName("ASCII");
	private static final Charset charsetUTF8 = Charset.forName("utf-8");

	private final String id;
	private final InetSocketAddress sa;
	private SocketChannel sc;

	public Client(String id, String serverAddress, int port) throws IOException {
		this.id = id;
		sa = new InetSocketAddress(serverAddress, port);
	}

	/**
	 * Parses buffer content with Jackson Streaming API
	 * 
	 * @param content JSOn to parse
	 * @return task data parsed
	 * @throws IOException
	 * @throws JsonParseException
	 */
	private static Task parse(String json) throws JsonParseException, IOException {
		Task task = new Task();
		JsonFactory jf = new JsonFactory();
		JsonParser jp = jf.createParser(json);
		jp.nextToken();
		while (jp.nextToken() != JsonToken.END_OBJECT) {
			String fieldname = jp.getCurrentName();
			jp.nextToken();
			if ("ComeBackInSeconds".equals(fieldname)) {
				task.setComeBackInSeconds(jp.getIntValue());
			} else if ("JobId".equals(fieldname)) {
				task.setJobId(jp.getText());
				task.setComeBackInSeconds(-1);
			} else if ("WorkerVersion".equals(fieldname)) {
				task.setWorkerVersion(jp.getText());
			} else if ("WorkerURL".equals(fieldname)) {
				task.setWorkerURL(jp.getText());
			} else if ("WorkerClassName".equals(fieldname)) {
				task.setWorkerClassName(jp.getText());
			} else if ("Task".equals(fieldname)) {
				task.setTask(jp.getText());
			} else {
				throw new IllegalStateException("Unrecognized field name: " + fieldname);
			}
		}
		jp.close();
		return task;
	}

	/**
	 * Requests a task to do
	 * 
	 * @return task data
	 * @throws IOException
	 */
	private Task requestTask() throws IOException {
		// send the request
		String request = "GET Task HTTP/1.1\r\n" + "Host: " + sa.getHostName() + "\r\n" + "\r\n";
		sc.write(charsetASCII.encode(request));

		// read the response
		ByteBuffer bb = ByteBuffer.allocate(50);
		HTTPReader reader = new HTTPReader(sc, bb);
		HTTPHeader header = reader.readHeader();
		System.out.println(header.toString());
		if (header.getCode() == 400) {
			throw new IllegalArgumentException("Bad request: " + request);
		} else if (header.getCode() != 200) {
			throw new UnexpectedException("Wrong http code: " + header.getCode());
		}
		ByteBuffer content = reader.readBytes(header.getContentLength());

		// parse json
		content.flip();
		return parse(charsetUTF8.decode(content).toString());
	}

	private void checkCode() throws IOException {
		ByteBuffer bb = ByteBuffer.allocate(50);
		HTTPReader reader = new HTTPReader(sc, bb);
		HTTPHeader header = reader.readHeader();
		System.out.println("Answer from server : " + header.getCode());
		//reader.readLineCRLF();
    }

	/**
	 * Tests if there are errors in the answer
	 * 
	 * @param answer String to test
	 * @return	the error message
	 * @throws JsonParseException if the parsing went wrong
	 * @throws IOException if something went wrong
	 */
	private String checkError(String answer) throws JsonParseException, IOException {
		if (answer == null) {
			return "Computation error";
		}

		if (!isJSON(answer)) {
			return "Answer is not valid JSON";
		}

		if (isNested(answer)) {
			return "Answer is nested";
		}

		return null;
	}

	/**
	 * Tests if the string is in json
	 * 
	 * @param string to test
	 * @return true if the string is in json, false otherwise
	 * @throws IOException if something went wrong
	 */
	public static boolean isJSON(String string) throws IOException {
		JsonFactory jf = new JsonFactory();
		JsonParser jp = jf.createParser(string);
		try {
			jp.nextToken();
			while(jp.nextToken() != JsonToken.END_OBJECT) {
				jp.nextToken();
			}
		} catch (JsonParseException jpe) {
			return false;
		}
		return true;
	}

	/**
	 * Tests if the string is nested
	 * 
	 * @param json the string to test
	 * @return true if the string is nested, false otherwise
	 * @throws JsonParseException if the parsing went wrong
	 * @throws IOException if something went wrong
	 */
	private boolean isNested(String json) throws JsonParseException, IOException {
		JsonFactory jf = new JsonFactory();
		JsonParser jp = jf.createParser(json);
		jp.nextToken();
		
		while ((jp.nextToken()) != JsonToken.END_OBJECT) {
			if ((jp.nextToken()) == JsonToken.START_OBJECT) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Creates the string answer to POST
	 * 
	 * @param task the task the client work on
	 * @param answer the answer the worker calculates
	 * @param error the error message is there is one
	 * @return the request
	 * @throws IOException if something went wrong
	 */
	private ByteBuffer createRequest(Task task, String answer, String error) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		JsonFactory jf = new JsonFactory();
		JsonGenerator jg = jf.createGenerator(baos);

		jg.writeStartObject();

		jg.writeStringField("JobId", String.valueOf(task.getJobId()));
		jg.writeStringField("WorkerVersion", task.getWorkerVersion());
		jg.writeStringField("WorkerURL", task.getWorkerURL());
		jg.writeStringField("WorkerClassName", task.getWorkerClassName());
		jg.writeStringField("Task", String.valueOf(task.getTask()));
		jg.writeStringField("ClientId", id);

		if (error == null) {
			jg.writeFieldName("Answer");
			jg.writeRawValue(answer);
		} else {
			jg.writeStringField("Error", error);
		}

		jg.writeEndObject();
		jg.close();
		
		String json = baos.toString();
		System.out.println(json);
		ByteBuffer jsonBuffer = charsetUTF8.encode(json);
		
		return jsonBuffer;
	}
	
	/**
	 * Sends the answer to the server
	 * 
	 * @param task
	 * @param answer
	 * @throws IOException
	 */
	private void sendAnswer(Task task, String answer) throws IOException {
		ByteBuffer jsonBuffer = createRequest(task, answer, checkError(answer));
		ByteBuffer content = ByteBuffer.allocate(Long.BYTES + Integer.BYTES);
		
		content.putLong(task.getJobId()).putInt(task.getTask());
		content.flip();
		if(content.remaining() + jsonBuffer.remaining() > 4096) {
			jsonBuffer = createRequest(task, answer, "Too Long");
		}
		
		int contentLength = content.remaining() + jsonBuffer.remaining();
		String header = "POST Answer HTTP/1.1\r\nHost: " + sa.getHostName() + "\r\nContent-Type: application/json\r\nContent-Length: " + contentLength + "\r\n\r\n";
		
		System.out.println(header);
		ByteBuffer bb = charsetASCII.encode(header);
		
		sc.write(bb);
		sc.write(content);
		sc.write(jsonBuffer);
	}

	/**
	 * Interacts with the server
	 * 
	 * @throws IOException if something went wrong
	 * @throws InterruptedException if the something is interrupted
	 * @throws ClassNotFoundException if the class was not found
	 * @throws IllegalAccessException if the class is not accessible
	 * @throws InstantiationException if the instantiation went wrong
	 */
	public void interact() throws IOException, InterruptedException, ClassNotFoundException, IllegalAccessException,
	        InstantiationException {
		Task task = new Task();
		Worker worker = null;
		do {
			sc = SocketChannel.open();
			sc.connect(sa);
			while (true) {
				try {
					task = requestTask();
				} catch(IllegalArgumentException e) {
					System.err.println(e.getMessage());
					task.setComeBackInSeconds(300);
				} catch(UnexpectedException e) {
					System.err.println(e.getMessage());
					task.setComeBackInSeconds(300);
				}
				try {
					Thread.sleep(task.getComeBackInSeconds());
				} catch (IllegalArgumentException e) {
					break;
				}
			}
			if (worker == null || task.getWorkerVersion() != worker.getVersion()
			        || task.getJobId() != worker.getJobId()) {
				worker = WorkerFactory.getWorker(task.getWorkerURL(), task.getWorkerClassName());
			}
			String answer;
			try {
				answer = worker.compute(task.getTask());
			} catch (Exception e) {
				answer = null;
			}
			sendAnswer(task, answer);
			checkCode();
			// Une tache par client pour l'instant
			//break;
			sc.close();
		} while (true);
	}

	public static void main(String[] args) throws JsonParseException, IOException, ClassNotFoundException,
	        IllegalAccessException, InstantiationException, InterruptedException {
//		String json = "{\"JobId\":11,\"WorkerVersion\":\"1.0\",\"WorkerURL\":\"http://igm.univ-mlv.fr/~carayol/WorkerPrimeV1.jar\",\"WorkerClassName\":\"upem.workerprime.WorkerPrime\",\"Task\":100}";
//		Task task = parse(json);
//		System.out.println("jobId: " + task.getJobId());
//		System.out.println("worker: " + task.getWorkerClassName() + ", " + task.getWorkerVersion() + ", "
//		        + task.getWorkerURL());
//		System.out.println("task: " + task.getTask());
//		Worker worker = null;
//		worker = WorkerFactory.getWorker(task.getWorkerURL(), task.getWorkerClassName());
//		System.out.println(worker.compute(task.getTask()));
		
		
		Client client = new Client("BastienMarwin", "ns364759.ip-91-121-196.eu", 8080);
		//Client client = new Client("BastienMarwin", "localhost", 7777);
		client.interact();
	}

}