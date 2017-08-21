package AA_plugins;

import java.util.*;


public class Ellipse {
	public ClusterChain chain;
	public float majorAxis;
	public float minorAxis;
	public float angleRad;

	public Ellipse(ClusterChain chain) {
		this.chain = chain;
	}

	public float getMajorMinorRatio() {
		return this.majorAxis/this.minorAxis;
	}

}
