package upem.jarret.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.rmi.UnexpectedException;

import upem.jarret.http.HTTPHeader;
import upem.jarret.http.HTTPReader;

import com.fasterxml.jackson.core.JsonFactory;
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
	 * @param content JSOn to parse
	 * @return task data parsed
	 * @throws IOException 
	 * @throws JsonParseException 
	 */
	private static Task parse(ByteBuffer content) throws JsonParseException, IOException {
		content.flip();
		String json = charsetUTF8.decode(content).toString();
		
		Task task = new Task();
		JsonFactory jf = new JsonFactory();
		JsonParser jp = jf.createParser(json);
		jp.nextToken();
		while(jp.nextToken() != JsonToken.END_OBJECT) {
			 String fieldname = jp.getCurrentName();
			 jp.nextToken();
			 if("ComeBackInSeconds".equals(fieldname)) {
				 task.setComeBackInSeconds(jp.getIntValue());
			 } else if("JobId".equals(fieldname)) {
				 task.setJobId(jp.getLongValue());
			 } else if("WorkerVersion".equals(fieldname)) {
				 task.setWorkerVersion(jp.getText()); 
			 } else if("WorkerURL".equals(fieldname)) {
				 task.setWorkerURL(jp.getText());
			 } else if("WorkerClassName".equals(fieldname)) {
				 task.setWorkerClassName(jp.getText());
			 } else if("Task".equals(fieldname)) {
				 task.setTask(jp.getIntValue());
			 } else {
				 throw new IllegalStateException("Unrecognized field name: "+fieldname);
			 }
		}
		jp.close();
		return task;
	}

	/**
	 * Requests a task to do
	 * @return task data
	 * @throws IOException
	 */
	public Task requestTask() throws IOException {
		// send the request
		String request = "GET Task HTTP/1.1\r\n"
				+ "Host: "+sa.getHostName()+"\r\n"
				+ "\r\n";
		sc.write(charsetASCII.encode(request));

		// read the response
		ByteBuffer bb = ByteBuffer.allocate(50);
		HTTPReader reader = new HTTPReader(sc, bb);
		HTTPHeader header = reader.readHeader();
		if(header.getCode() == 400) {
			throw new IllegalArgumentException("Bad request: "+request);
		} else if(header.getCode() != 200) {
			throw new UnexpectedException("Wrong http code: "+header.getCode());
		}
		ByteBuffer content = reader.readBytes(header.getContentLength());

		// parse json
		return parse(content);
	}

	public void sendAnswer(String answer) {
		// TODO Auto-generated method stub
		// test si la r�ponse fait la bonne taille
		// test si c'est bien du json
		// test si y'a pas d'OBJECT dans la r�ponse
		// test si la r�ponse n'est pas null
		
		// construit la r�ponse JSON et envoie
	}
}