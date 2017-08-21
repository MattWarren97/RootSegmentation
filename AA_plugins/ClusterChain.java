package AA_plugins;

import java.util.*;


public class ClusterChain {
	public ArrayList<Cluster> clusters;
	public Cluster firstCluster;
	public Cluster lastCluster;
	public Ellipse ellipse;

	public ClusterChain(ArrayList<Cluster> clusters) {
		this.clusters = clusters;
		this.firstCluster = clusters.get(0);
		this.lastCluster = clusters.get(clusters.size() - 1);
		
	}

	public void setEllipse(Ellipse ell) {
		this.ellipse = ell;
	}

	public int getStartSlice() {
		return this.firstCluster.z;
	}
	
	public int getLastSlice() {
		return this.lastCluster.z;
	}
	
	//Adds cc to the end of this chain.
	public void append(ClusterChain cc) {
		this.clusters.addAll(cc.clusters);
		this.lastCluster = clusters.get(clusters.size() - 1);
	}
	
	public ArrayList<Point> getPointsList() {
		ArrayList<Point> points = new ArrayList<Point>();
		for (Cluster c : this.clusters) {
			for (Point p: c.points) {
				points.add(p);
			}
		}
		return points;
	}
}
