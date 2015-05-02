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
	private final SocketChannel sc;

	public Client(String id, String serverAddress, int port) throws IOException {
		this.id = id;
		sa = new InetSocketAddress(serverAddress, port);
		sc = SocketChannel.open();
		sc.connect(sa);
	}

	/**
	 * Parses buffer content with Jackson Streaming API
	 * 
	 * @param content
	 *            JSOn to parse
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

	public void interact() throws IOException, InterruptedException, ClassNotFoundException, IllegalAccessException,
	        InstantiationException {
		Task task;
		Worker worker = null;
		do {
			while (true) {
				task = requestTask();
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
			break;
		} while (true);
	}

	private void checkCode() throws IOException {
		//TODO
    }

	private String checkError(String answer) throws JsonParseException, IOException {
		// TODO
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

	private boolean isJSON(String string) throws JsonParseException, IOException {

		return true;
	}

	private boolean isNested(String json) throws JsonParseException, IOException {
		JsonFactory jf = new JsonFactory();
		JsonParser jp = jf.createParser(json);
		JsonToken token = jp.nextToken();

		while (token != null) {
			if (token == JsonToken.START_OBJECT) {
				return false;
			}
			token = jp.nextToken();
		}
		return true;
	}

	private String createRequest(Task task, String answer, String error) throws IOException {
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
		ByteBuffer jsonBuffer = charsetUTF8.encode(json);
		String header = "POST Answer HTTP/1.1\r\nHost: " + sa.getAddress() + "\r\nContent-Type: application/json\r\nContent-Length: " + jsonBuffer.remaining() + "\r\n\r\n";
		
		return header + json;
	}
	
	private void sendAnswer(Task task, String answer) throws IOException {
		String error = checkError(answer);
		String request = createRequest(task, answer, error);
		
		ByteBuffer bb = charsetUTF8.encode(request);
		
		if(bb.remaining() > 4096) {
			System.err.println("Too long");
			error = "Too Long";
			request = createRequest(task, answer, error);
			bb = charsetUTF8.encode(request);
		}
		
		System.out.println(request);
		
		sc.write(bb);
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
		
		
		Client client = new Client("Bob", "ns364759.ip-91-121-196.eu", 8080);
		client.interact();
	}

}