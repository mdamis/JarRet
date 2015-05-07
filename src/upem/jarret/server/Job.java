package upem.jarret.server;

public class Job {
	private final String id;
	private final String taskNumber;
	private final String description;
	private final String priority;
	private final String workerVersion;
	private final String workerURL;
	private final String workerClassName;
	
	public Job(String id, String taskNumber, String description, String priority, String workerVersion, String workerURL, String workerClassName) {
		this.id = id;
		this.taskNumber = taskNumber;
		this.description = description;
		this.priority = priority;
		this.workerVersion = workerVersion;
		this.workerURL = workerURL;
		this.workerClassName = workerClassName;
	}

	public String getId() {
		return id;
	}

	public String getTaskNumber() {
		return taskNumber;
	}

	public String getDescription() {
		return description;
	}

	public String getPriority() {
		return priority;
	}

	public String getWorkerVersion() {
		return workerVersion;
	}

	public String getWorkerURL() {
		return workerURL;
	}

	public String getWorkerClassName() {
		return workerClassName;
	}
}
