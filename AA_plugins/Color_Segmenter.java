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
	static float maxCenterDistance = 5;
	static int minClusterChainLength = 10;
	
	static {
		lut = new int[256];
		for (int i = 0; i< 256; i++) {
			lut[i] = i/16;
		}
	}
	

	HashMap<Integer, ArrayList<Cluster>> sliceClusterMap;
	HashMap<Integer, Integer> differenceCounts;
	HashMap<Integer, HashMap<Cluster, Cluster>> pairedClustersBySlice;



	/*public void run(ImageProcessor ip) {
		this.sliceClusterMap = new HashMap<Integer, ArrayList<Cluster>>();

		//could easily make this multithreaded I think... (TODO)
		ImageStack stack = this.image.getStack();
		for (int i = 1; i <= stack.getSize(); i++) {
			System.out.println("Run on Slice: " + i);
			ImageProcessor nextSlice = stack.getProcessor(i);
			convertTo4Bit(nextSlice);
			connectivityAnalysis(nextSlice);
			sliceClusterMap.put(i, largeClusters);
		}
		//ImagePlus image = new ImagePlus("4bitimage", stack);
		//image.show();
		System.out.println("Finished calculating clusters for each image. Now trying to link them.");

		printDifferenceCounts();

	}*/
	
	//multithreaded run version
	public void run(ImageProcessor ip) {
		this.sliceClusterMap = new HashMap<Integer, ArrayList<Cluster>>(); //does this need to be synchronised?
		this.pairedClustersBySlice = new HashMap<Integer, HashMap<Cluster, Cluster>>();
		ImageStack stack = this.image.getStack(); //this too..?
		int processors = Runtime.getRuntime().availableProcessors();
		float slicesPerThread = (float) stack.getSize() / (float) processors;
		float runningSlicesComputedWithRemainder = 0; //to ensure that you don't have 10,10,10,19 slices for each thread - will be even now.
		System.out.println(processors + " processors.");
		int startSlice = 1;
		Thread[] threads = new Thread[processors];
		for (int i = 0; i < processors; i++) {
			runningSlicesComputedWithRemainder += slicesPerThread;
			int end = (int) runningSlicesComputedWithRemainder;
			Thread t = new Thread(new ObjectFinder(startSlice, end, stack, this));
			t.start();
			threads[i] = t;
			startSlice = end + 1;
		}
		
		for (int i = 0; i < processors; i++) {
			try {
				threads[i].join();
			}
			catch (InterruptedException ie) {
				System.out.println("Thread " + i + " has joined!");
			}
		}
		
		
		//wait for the other threads to be finished...
		System.out.println("Finished calculatign clusters for each image. Now trying to link them!");
		findConnectedClusters();
		findChainedClusters();
	}			
			
		
	public void findConnectedClusters() {
		//500x500x300 - {0=259633, 1=169307, 2=20573, 3=3660, 4=1021, 5=362, 6=101, 7=34, 8=14, 9=10, 10=1, 14=1} [more than 1 each]
		//500x500x300 - {0=257548, 1=131425, 2=15536, 3=2866, 4=824, 5=297, 6=86, 7=32, 8=8, 9=8, 10=1, 14=1} [only 1 each].
		//A_cropped - {0=1891759, 1=395663, 2=19213, 3=1285, 4=303, 5=153, 6=130, 7=283, 8=701, 9=892, 10=573, 11=239, 12=66, 13=15, 14=1, 15=13}
		HashMap<Cluster, Cluster> connectedClusters;
		for (int i = 1; i <= this.image.getStackSize() - 1; i++) {
			
			connectedClusters = new HashMap<Cluster, Cluster>();
			ArrayList<Cluster> clusters1 = sliceClusterMap.get(i);
			ArrayList<Cluster> clusters2 = sliceClusterMap.get(i+1);
			for (Cluster c1: clusters1) {
				float minDifference = -1;
				Cluster bestCluster = null;
				for (Cluster c2 : clusters2) {
					Float difference = compareClusters(c1, c2);
					if (difference == null) {
						continue;
					}
					
					if (difference < minDifference || minDifference == -1) {
						minDifference = difference;
						bestCluster = c2;
					}
				}
				if (minDifference != -1) {
					connectedClusters.put(c1, bestCluster);
				}
			}
			System.out.println("Completed difference mapping for slice " + i);
			pairedClustersBySlice.put(i, connectedClusters);
		}
	}
	
	//minimum length of chains found will be 2 bc it is known they are connected to at least one...
	
	//problem is that A-B-C-D-E-F_G... will also get picked up as B-C_D_E_F_G.
	//I could remove B-C_D_E_F_G once I've found the chain A-B_C_D_E_F_G,
	//but it is entirely possible to have: A-B-C-D-E-F-G AND A2-B-C-D-E-F-G-... 
	//so I guess B and it's consequent chain should be stored as a known chain that can be linked to again...
	//(same thing will need to be done for C-D-E-F_G... and D-E-F-G... etc.)
	//that's a pain!
	
	//results:
	//Cluster lengths: {2=95660, 3=64261, 4=45500, 5=33396, 6=24993, 7=19255, 8=14995, 9=11668, 10=9163, 11=7350, 12=5746, 13=4562, 14=3582, 15=2839, 16=2278, 17=1818, 18=1454, 19=1166, 20=884, 21=741, 22=584, 23=472, 24=396, 25=318, 26=269, 27=225, 28=186, 29=159, 30=129, 31=94, 32=81, 33=73, 34=70, 35=61, 36=48, 37=47, 38=35, 39=32, 40=34, 41=29, 42=27, 43=27, 44=22, 45=19, 46=20, 47=22, 48=19, 49=15, 50=12, 51=9, 52=8, 53=8, 54=7, 55=5, 56=5, 57=4, 58=4, 59=4, 60=3, 61=3, 62=4, 63=3, 64=3, 65=3, 66=3, 67=3, 68=4, 69=4, 70=4, 71=6, 72=6, 73=3, 74=3, 75=2, 76=1, 77=1, 78=1, 79=1, 80=1, 81=1, 82=2, 83=2, 84=3, 85=2, 86=2, 87=1, 88=1, 89=1, 90=1, 91=1, 92=1, 93=1, 94=1, 95=3, 96=2, 97=1, 98=1, 99=1, 100=1, 101=2, 102=1, 103=1, 104=1, 105=1, 106=1, 107=2, 108=2, 109=2, 110=2, 111=1, 112=1, 113=1, 114=1, 115=1, 116=1, 117=1, 118=1, 119=1, 120=1, 121=1, 122=1, 123=1, 124=1, 125=1, 126=1, 127=1, 128=1, 129=1, 130=1, 131=1, 132=1, 133=1, 134=2, 135=3, 136=2, 137=3, 138=3, 139=2, 140=1, 141=1, 142=2}

	//These aren't consistent with what I expected... How can there be 2 at length 142, but only 1 at length 141?
	//need to fix this I suppose.
	//Then I can get to visualising the results.
	public void findChainedClusters() {
		HashMap<Integer, Integer> clusterLengths = new HashMap<Integer, Integer>();
		int stackSize = this.image.getStackSize();
		for (int i = 1; i <= stackSize - minClusterChainLength; i++) {
			HashMap<Cluster, Cluster> connectedClusters = pairedClustersBySlice.get(i);
			
			for (Cluster firstKey: connectedClusters.keySet()) {
				Cluster key = firstKey;
				Cluster next;
				int length = 0;
				do {
					if (key.getSliceNumber() == stackSize) {
						length++;
						break;
					}
					next = pairedClustersBySlice.get(key.getSliceNumber()).get(key);
					key = next;
					length++;
				}
				while(key != null);
				if (length > minClusterChainLength) {
					System.out.println("Cluster " + firstKey + " had a chainLength of " + length);
				}
				if (clusterLengths.containsKey(length)) {
					clusterLengths.put(length, clusterLengths.get(length) + 1);
				}
				else {
					clusterLengths.put(length, 1);
				}
			}
		}
		
		System.out.println("Cluster lengths: " + clusterLengths);
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
		//changed areaDiference -- now divides by c1+c2 -- because otherwise it seems inconsitent TODO.

		float areaDifference = (float) Math.abs((c1.area - c2.area)/(c1.area + c2.area));

		float comparison = colourDifference*colourDifferenceWeight + aspectRatioChange*aspectRatioDifferenceWeight + areaDifference*areaDifferenceWeight;
		return comparison;
	}

}

class ObjectFinder implements Runnable {
	int start, end;
	ImageStack stack;
	Color_Segmenter cs;
	
	ArrayList<Cluster> clusters;
	ArrayList<Cluster> largeClusters;
	ArrayList<Point> toBeProcessed;
	ArrayList<Point> sameClusterToBeProcessed;
	Point[][] points;
	int sliceNumber;
		
	public ObjectFinder(int start, int end, ImageStack stack, Color_Segmenter cs) {
		this.start = start;
		this.end = end;
		this.stack = stack;
		this.cs = cs;

	}
	public void run() {
		System.out.println("Thread between " + start + "-" + end + " has begun.");
		for (sliceNumber = start; sliceNumber <= end; sliceNumber++) {
			System.out.println("Run on Slice: " + sliceNumber);
			ImageProcessor nextSlice = stack.getProcessor(sliceNumber);
			convertTo4Bit(nextSlice);
			connectivityAnalysis(nextSlice);
			cs.sliceClusterMap.put(sliceNumber, largeClusters);
		}
	}
	
	public void convertTo4Bit(ImageProcessor ip) {
		//copying some lines from the threshold code (https://imagej.nih.gov/ij/source/ij/plugin/Thresholder.java)
		ip.applyTable(Color_Segmenter.lut);
	}



	public void connectivityAnalysis(ImageProcessor ip) {
		int xMin = 0;
		int yMin = 0;
		int xMax = this.cs.X-1;
		int yMax = this.cs.Y-1;
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
				this.clusters.add(new Cluster(currentPoint, sliceNumber));
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
			if (c.getArea() >= Color_Segmenter.minClusterSize) {
				largeClusters.add(c);
			}
		}
		System.out.println("Points: " + points.length*(points[0].length));
		System.out.println("Clusters: " + clusters.size());
		System.out.println(Color_Segmenter.minClusterSize + "-size clusters: " + largeClusters.size());
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
	public int z;

	public Cluster(Point p, int z) {
		this.points = new ArrayList<Point>();
		this.neighbours = new ArrayList<Cluster>();
		this.futureNeighbours = new ArrayList<Point>();
		this.addPoint(p);
		this.colour = p.value;
		this.z = z;
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
	
	public int getSliceNumber() {
		return this.z;
	}
}
