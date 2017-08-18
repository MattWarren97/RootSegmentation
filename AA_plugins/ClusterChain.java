package AA_plugins;

import java.util.*;


public class ClusterChain {
	public ArrayList<Cluster> clusters;
	public Cluster firstCluster;
	public Cluster lastCluster;

	public ClusterChain(ArrayList<Cluster> clusters) {
		this.clusters = clusters;
		this.firstCluster = clusters.get(0);
		this.lastCluster = clusters.get(clusters.size() - 1);
		
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
}
