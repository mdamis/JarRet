package upem.jarret.server;

import java.nio.ByteBuffer;

public class Attachment {
	private ByteBuffer bb = ByteBuffer.allocate(1024);

	public ByteBuffer getBb() {
		return bb;
	}
	
}
