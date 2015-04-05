package upem.jarret.client;

public class Task {
	private String _JobId;
	private String _WorkerVersion;
	private String _WorkerURL;
	private String _WorkerClassName;
	private int _Task;
	private int _ComeBackInSeconds;
	
	public String getJobId() { return _JobId; }
	public String getWorkerVersion() { return _WorkerVersion; }
	public String getWorkerURL() { return _WorkerURL; }
	public String getWorkerClassName() { return _WorkerClassName; }
	public int getTask() { return _Task; }
	public int getComeBackInSeconds() { return _ComeBackInSeconds; }
	
	public void setJobId(String jobId) { _JobId = jobId; }
	public void setWorkerVersion(String wv) { _WorkerVersion = wv; }
	public void setWorkerURL(String wURL) { _WorkerURL = wURL; }
	public void setWorkerClassName(String wcn) { _WorkerClassName = wcn; }
	public void setTask(int t) { _Task = t; }
	public void setComeBackInSeconds(int cbis) { _ComeBackInSeconds = cbis; }
}
