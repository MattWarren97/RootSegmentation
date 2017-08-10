package AA_plugins;

public class Point {
	public int x;
	public int y;
	public int value;
	public Cluster cluster;
	public boolean hasCluster;
	public boolean accountedForInSameCluster;
	public boolean accountedForInNewCluster;

	public Point (int x, int y, int v) {
		this.x = x;
		this.y = y;
		this.setValue(v);
		this.accountedForInSameCluster = false;
		this.accountedForInNewCluster = false;
	}

	public void setValue(int v) {
		this.value = v;
	}

	public void setCluster(Cluster c) {
		this.cluster = c;
		this.hasCluster = true;
	}

	public String toString() {
		return "(" + this.x + ", " + this.y + ")";
	}
}