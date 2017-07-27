package AA_plugins;

import ij.*; //   \\Cseg_2\erc\ADMIN\Programmes\Fiji_201610_JAVA8.app\jars\ij-1.51g.jar needs to be added to classpath when compiling(-cp))
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import java.awt.*;
import java.util.Arrays;
import ij.macro.Interpreter;
import ij.plugin.*;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.gui.ImageWindow;
import ij.plugin.filter.ThresholdToSelection;
import ij.gui.Roi;
import ij.plugin.filter.EDM;
import ij.gui.OvalRoi;
import ij.measure.Calibration;
import externalPluginCopies.*;
import externalPluginCopies.FilterPlugin.FilterType;

import java.util.*;

public class Color_Segmenter extends SegmentationPlugin implements PlugInFilter {
	


	static int[] lut;
	static int minClusterSize = 20;

	static float colourDifferenceWeight = 1;
	static float areaDifferenceWeight = 1;
	static float aspectRatioDifferenceWeight = 1;
	static float maxCenterDistance = 20;

	static {
		lut = new int[256];
		for (int i = 0; i< 256; i++) {
			lut[i] = i/16;
		}
	}

	ArrayList<Cluster> clusters;
	ArrayList<Cluster> largeClusters;
	ArrayList<Point> toBeProcessed;
	ArrayList<Point> sameClusterToBeProcessed;
	HashMap<Integer, ArrayList<Cluster>> sliceClusterMap;
	HashMap<Integer, Integer> differenceCounts;

	Point[][] points;

	public void run(ImageProcessor ip) {
		this.sliceClusterMap = new HashMap<Integer, ArrayList<Cluster>>();

		ImageStack stack = this.image.getStack();
		for (int i = 1; i <= stack.getSize(); i++) {
			System.out.println("Run on Slice: " + i);
			ImageProcessor nextSlice = stack.getProcessor(i);
			convertTo4Bit(nextSlice);
			connectivityAnalysis(nextSlice);
			sliceClusterMap.put(i, largeClusters);
		}
		ImagePlus image = new ImagePlus("4bitimage", stack);
		image.show();
		System.out.println("Finished calculating clusters for each image. Now trying to link them.");

		differenceCounts = new HashMap<Integer, Integer>();
		int nullCount = 0;
		int nonNullCount = 0;
		for (int i = 1; i <= stack.getSize() - 1; i++) {
			ArrayList<Cluster> iClusters = sliceClusterMap.get(i);
			ArrayList<Cluster> ippClusters = sliceClusterMap.get(i+1);
			for (Cluster c1: iClusters) {
				for (Cluster c2 : ippClusters) {
					if (c1 == null) {
						System.out.println("c1 is null!");
					}
					if (c2 == null) {
						System.out.println("c2 is null!");
					}
					//System.out.println(c1.center + " / " + c1.area + " / " + c1.aspectRatio + " / " + c1.colour);
					Float difference = compareClusters(c1, c2);
					if (difference == null) {
						nullCount++;
						continue;
					}
					nonNullCount++;
					//automatically round down.
					Integer floorDifference = (int) ((float) difference);
					if (differenceCounts.keySet().contains(floorDifference)) {
						differenceCounts.put(floorDifference, differenceCounts.get(floorDifference) + 1);
					}
					else {
						differenceCounts.put(floorDifference, 1);
					}
				}
			}
			System.out.println("Completed difference mapping for slice " + i);
		}

		System.out.println("Finally! " + differenceCounts);

	}

	public void convertTo4Bit(ImageProcessor ip) {
		//copying some lines from the threshold code (https://imagej.nih.gov/ij/source/ij/plugin/Thresholder.java)
		ip.applyTable(Color_Segmenter.lut);
	}



	public void connectivityAnalysis(ImageProcessor ip) {
		int xMin = 0;
		int yMin = 0;
		int xMax = X-1;
		int yMax = Y-1;
		clusters = new ArrayList<Cluster>();
		largeClusters = new ArrayList<Cluster>();
		toBeProcessed = new ArrayList<Point>();
		sameClusterToBeProcessed = new ArrayList<Point>();

		points = new Point[xMax+1][yMax+1];
		for (int i = 0; i < xMax+1; i++) {
			for (int j = 0; j < yMax+1; j++) {
				points[i][j] = new Point(i, j, ip.get(i, j));
			}
		}

		toBeProcessed.add(points[0][0]);

		while(toBeProcessed.size() != 0 || sameClusterToBeProcessed.size() != 0) {
			boolean sameCluster = false;
			Point currentPoint;
			if (sameClusterToBeProcessed.size() != 0) {
				currentPoint = sameClusterToBeProcessed.get(0);
				sameCluster = true;
			}
			else {
				currentPoint = toBeProcessed.get(0);
				this.clusters.add(new Cluster(currentPoint));
			}
			//System.out.println("New point is " + currentPoint);
			//System.out.println("Length of toBeProcessed, sameClusterToBeProcessed is " + toBeProcessed.size() + "," + sameClusterToBeProcessed.size());

			//first try to the right of this point --
			if(currentPoint.x + 1 <= xMax) {
				considerPoint(currentPoint.x+1, currentPoint.y, currentPoint);
			}
			//next try the point to the left of this point --
			if (currentPoint.x - 1  >= xMin) {
				considerPoint(currentPoint.x-1, currentPoint.y, currentPoint);
			}
			//next try below this point --
			if (currentPoint.y + 1 <= yMax) {
				considerPoint(currentPoint.x, currentPoint.y+1, currentPoint);
			}
			//finally try above this point --
			if (currentPoint.y - 1 >= yMin) {
				considerPoint(currentPoint.x, currentPoint.y-1, currentPoint);
			}

			//then remove that last point from its list.
			if (sameCluster) {
				sameClusterToBeProcessed.remove(0);
			}
			else {
				toBeProcessed.remove(0);
			}
		}

		//now going through and sorting out 'future neighbours' for each cluster.
		for (Cluster c: clusters) {
			c.postProcessing();
		}

		for (Cluster c: clusters) {
			if (c.getArea() >= minClusterSize) {
				largeClusters.add(c);
			}
		}
		System.out.println("Points: " + points.length*(points[0].length));
		System.out.println("Clusters: " + clusters.size());
		System.out.println("20-size clusters: " + largeClusters.size());
		clusters = null;
		points = null;
	}

	public void considerPoint(int x, int y, Point previous) {
		//System.out.println("Considering " + x + ", " + y);
		Point newPoint = points[x][y];
		if (newPoint.hasCluster) {
			//System.out.println("Ignoring " + newPoint + " - it has a cluster!");
			return;
		}
		if (newPoint.value == previous.value) {
			previous.cluster.addPoint(newPoint);
			if (!sameClusterToBeProcessed.contains(newPoint)) {
				sameClusterToBeProcessed.add(newPoint);
				//System.out.println("Adding " + newPoint + " to sameClusterToBeProcessed");

			}
		}
		else {
			previous.cluster.addNeighbour(newPoint);
			if (!toBeProcessed.contains(newPoint)) {
				toBeProcessed.add(newPoint);
				//System.out.println("Adding " + newPoint + " to toBeProcessed");
			}
		}
	}


	public Float compareClusters(Cluster c1, Cluster c2) {
		float centerXDist = Math.abs(c1.center[0] - c2.center[0]);
		float centerYDist = Math.abs(c1.center[1] - c2.center[1]);

		float centerDifference = (float) Math.sqrt(Math.pow(centerXDist, 2) + Math.pow(centerYDist, 2));

		if (centerDifference > Color_Segmenter.maxCenterDistance) {
			return null;
		}

		float colourDifference = (float) Math.abs(c1.colour - c2.colour);
		float aspectRatioChange = (float) Math.abs((c1.aspectRatio - c2.aspectRatio)/(c1.aspectRatio + c2.aspectRatio));
		float areaDifference = (float) Math.abs((c1.area - c2.area)/c1.area);

		float comparison = colourDifference*colourDifferenceWeight + aspectRatioChange*aspectRatioDifferenceWeight + areaDifference*areaDifferenceWeight;
		return comparison;
	}

}

class Point {
	public int x;
	public int y;
	public int value;
	public Cluster cluster;
	public boolean hasCluster;

	public Point (int x, int y, int v) {
		this.x = x;
		this.y = y;
		this.setValue(v);
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



class Cluster {
	public ArrayList<Point> points;
	public ArrayList<Cluster> neighbours;
	private ArrayList<Point> futureNeighbours;
	public int colour;
	public int area;
	public float aspectRatio;
	public float[] center;

	public Cluster(Point p) {
		this.points = new ArrayList<Point>();
		this.neighbours = new ArrayList<Cluster>();
		this.futureNeighbours = new ArrayList<Point>();
		this.addPoint(p);
		this.colour = p.value;
	}

	public void addPoint(Point p) {
		if (!this.points.contains(p)) {
			this.points.add(p);
			p.setCluster(this);
		}
	}

	public void addNeighbour(Point p) {
		this.futureNeighbours.add(p);
	}

	public int getArea() {
		return this.points.size();
	}

	public String toString() {
		String toReturn = "Cluster starting at " + points.get(0);
		toReturn = toReturn + " with Area: " + area;
		toReturn = toReturn + ", AspectRatio: " + aspectRatio;
		toReturn = toReturn + ", Center: " + this.center[0] + "," + this.center[1];
		toReturn = toReturn + ", NeighbourCount: " + countNeighbours();
		toReturn = toReturn + ", Colour: " + colour;
		return toReturn;
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

	public int getColour() {
		return this.colour;
	}

	public void postProcessing() {
		for (Point p: futureNeighbours) {
			if (!neighbours.contains(p.cluster)) {
				if (p.cluster.getArea() >= Color_Segmenter.minClusterSize) {
					//we don't care much if it borders an outlier pixel
					neighbours.add(p.cluster);
				}
			}
		}
		futureNeighbours = null;
		calculateValues();
	}

	public int countNeighbours() {
		return this.neighbours.size();
	}

	public void calculateValues() {
		this.area = getArea();
		this.aspectRatio = getAspectRatio();
		this.center = getCenter();
	}
}
