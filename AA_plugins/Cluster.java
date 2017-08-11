package AA_plugins;

import java.util.*;


public class Cluster {
	public ArrayList<Point> points;
	public int value;
	public int area;
	public float aspectRatio;
	public float[] center;
	public int z;
	public boolean calculatedValues;

	public Cluster(Point p, int z) {
		this.points = new ArrayList<Point>();
		this.value = p.value;
		this.addPoint(p, true);
		this.z = z;
		this.calculatedValues = false;
	}

	public void addPoint(Point p, boolean setPointCluster) {
		if (!this.points.contains(p)) {
			this.points.add(p);
			//a point can be in multiple clusters -- but it only owns a cluster of the same value.
			if (setPointCluster) {
				p.setCluster(this);
			}
		}
	}

	public int getArea() {
		return this.points.size();
	}

	public String toString() {
		//this.calculateValues();
		String toReturn = "Cluster center at (" + ((int) this.center[0]) + "," + ((int) this.center[1]) + "," + this.z + ") - ";
		toReturn = toReturn + " with Area: " + area;
		toReturn = toReturn + ", AspectRatio: " + aspectRatio;
		toReturn = toReturn + ", Center: " + this.center[0] + "," + this.center[1];
		toReturn = toReturn + ", Value: " + value;
		return toReturn+"\n";
	}

	public float getAspectRatio() {
		int[] aspectRatio = new int[2];
		Point initial = points.get(0);
		int minX = initial.x, minY = initial.y, maxX = initial.x, maxY = initial.y;
		for (Point p: points) {
			if (p.x < minX) {
				minX = p.x;
			}
			else if (p.x > maxX) {
				maxX = p.x;
			}
			if (p.y < minY) { 
				minY = p.y;
			}
			else if (p.y > maxY) {
				maxY = p.y;
			}
		}

		aspectRatio[0] = maxX - minX;
		aspectRatio[1] = maxY - minY;
		return (float)aspectRatio[0]/(float)aspectRatio[1];
	}

	public float[] getCenter() {
		float[] center = new float[2];
		int xSum = 0, ySum = 0;
		int size = getArea();
		for (Point p : points) {
			xSum += p.x;
			ySum += p.y;
		}
		center[0] = (float) xSum/(float)size;
		center[1] = (float) ySum/(float)size;

		return center;
	}

	public int getValue() {
		return this.value;
	}

	public void postProcessing() {
		calculateValues();
	}

	private void calculateValues() {
		this.calculatedValues = true;
		this.area = getArea();
		this.aspectRatio = getAspectRatio();
		this.center = getCenter();
	}
	
	public int getSliceNumber() {
		return this.z;
	}
}
