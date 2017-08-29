package AA_plugins;

import ij.*; //   \\Cseg_2\erc\ADMIN\Programmes\Fiji_201610_JAVA8.app\jars\ij-1.51g.jar needs to be added to classpath when compiling(-cp))
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import java.awt.*;
import javax.swing.*;
import java.awt.event.*;
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

public class Color_Segmenter extends SegmentationPlugin implements PlugInFilter, Runnable {

	static int[] lut;
	static int minClusterSize;

	//early processing constants
	static float colourDifferenceWeight;
	static float areaDifferenceWeight;
	static float aspectRatioDifferenceWeight;
	static float maxCenterDistance;
	static int maximumColourDifference = 1; //no of bins apart.
	
	static {
		int binsRequired = 32;
		float divisor = (float)256/(float) binsRequired;
		lut = new int[256];
		for (int i = 0; i< 256; i++) {
			lut[i] = (int) ((float)i/divisor);
		}
	}
	
	//post processing constants
	static int minClusterChainLength;
	static float majorMinorRatioLimit;
	static float chainJoiningScaler;
	
	HashMap<Integer, ArrayList<Cluster>> sliceClusterMap;
	HashMap<Integer, Integer> differenceCounts;
	HashMap<Integer, HashMap<Cluster, Cluster>> pairedClustersBySlice;
	HashMap<Cluster, Cluster> replacements;
	
	HashMap<Integer, HashMap<Integer, ArrayList<Cluster>>> sliceNumber_clusterValue_clusters_MAP;
	HashMap<Integer, ArrayList<ClusterChain>> chainLengths_chains_MAP;
	
	//multithreaded run version
	boolean printMatches, printDifferences;
	int minSliceNumber, maxSliceNumber, minValue, maxValue, minArea, maxArea;
	int minCenterX, maxCenterX, minCenterY, maxCenterY;
	
	LimitSelecterFrame limitSelecterFrame;
	
	//public String toString() {
		//String toReturn = "";
		
		
	public Color_Segmenter(ImagePlus iPlus, ColorBlobOptions options) {
		this.image = iPlus;
		this.duplicateImage();
		if (options.printMatches) {
			ObjectFinder.rootLowerBound = options.rootLowerBound;
			ObjectFinder.rootUpperBound = options.rootUpperBound;
			this.printMatches = true;
			Color_Segmenter.minClusterSize = options.minClusterSize;
			Color_Segmenter.maxCenterDistance = options.maxCenterDistance;
			Color_Segmenter.areaDifferenceWeight = options.areaDifferenceWeight;
			Color_Segmenter.aspectRatioDifferenceWeight = options.aspectRatioDifferenceWeight;
			Color_Segmenter.colourDifferenceWeight = options.colourDifferenceWeight;
			Color_Segmenter.minClusterChainLength = options.minClusterChainLength;
			Color_Segmenter.majorMinorRatioLimit = options.majorMinorRatioLimit;
			Color_Segmenter.chainJoiningScaler = options.chainJoiningScaler;

			this.printDifferences = false;

			this.minSliceNumber = options.minSliceNumber;
			this.maxSliceNumber = options.maxSliceNumber;
			this.minValue = options.minValue;
			this.maxValue = options.maxValue;
			this.minArea = options.minArea;
			this.maxArea = options.maxArea;
			this.minCenterX = options.minCenterX;
			this.maxCenterX = options.maxCenterX;
			this.minCenterY = options.minCenterY;
			this.maxCenterY = options.maxCenterY;

		}
		else {
			ObjectFinder.rootLowerBound = options.rootLowerBound;
			ObjectFinder.rootUpperBound = options.rootUpperBound;
			this.printMatches = true;
			Color_Segmenter.minClusterSize = options.minClusterSize;
			Color_Segmenter.maxCenterDistance = options.maxCenterDistance;
			Color_Segmenter.areaDifferenceWeight = options.areaDifferenceWeight;
			Color_Segmenter.aspectRatioDifferenceWeight = options.aspectRatioDifferenceWeight;
			Color_Segmenter.colourDifferenceWeight = options.colourDifferenceWeight;
			Color_Segmenter.minClusterChainLength = options.minClusterChainLength;
			Color_Segmenter.majorMinorRatioLimit = options.majorMinorRatioLimit;
			Color_Segmenter.chainJoiningScaler = options.chainJoiningScaler;

			this.printDifferences = false;
		}
	}

	public void run(ImageProcessor ip) {
		System.out.println("Creating gui..");
		this.limitSelecterFrame = new LimitSelecterFrame(this);
		System.out.println("Created gui!");
	}
	
	public void run(ColorBlobOptions options) {
		this.updateImage(true);
		if (options.printMatches) {
			ObjectFinder.rootLowerBound = options.rootLowerBound;
			ObjectFinder.rootUpperBound = options.rootUpperBound;
			this.printMatches = true;
			Color_Segmenter.minClusterSize = options.minClusterSize;
			Color_Segmenter.maxCenterDistance = options.maxCenterDistance;
			Color_Segmenter.areaDifferenceWeight = options.areaDifferenceWeight;
			Color_Segmenter.aspectRatioDifferenceWeight = options.aspectRatioDifferenceWeight;
			Color_Segmenter.colourDifferenceWeight = options.colourDifferenceWeight;
			Color_Segmenter.minClusterChainLength = options.minClusterChainLength;
			Color_Segmenter.majorMinorRatioLimit = options.majorMinorRatioLimit;
			Color_Segmenter.chainJoiningScaler = options.chainJoiningScaler;

			this.printDifferences = false;

			this.minSliceNumber = options.minSliceNumber;
			this.maxSliceNumber = options.maxSliceNumber;
			this.minValue = options.minValue;
			this.maxValue = options.maxValue;
			this.minArea = options.minArea;
			this.maxArea = options.maxArea;
			this.minCenterX = options.minCenterX;
			this.maxCenterX = options.maxCenterX;
			this.minCenterY = options.minCenterY;
			this.maxCenterY = options.maxCenterY;

		}
		else {
			ObjectFinder.rootLowerBound = options.rootLowerBound;
			ObjectFinder.rootUpperBound = options.rootUpperBound;
			this.printMatches = true;
			Color_Segmenter.minClusterSize = options.minClusterSize;
			Color_Segmenter.maxCenterDistance = options.maxCenterDistance;
			Color_Segmenter.areaDifferenceWeight = options.areaDifferenceWeight;
			Color_Segmenter.aspectRatioDifferenceWeight = options.aspectRatioDifferenceWeight;
			Color_Segmenter.colourDifferenceWeight = options.colourDifferenceWeight;
			Color_Segmenter.minClusterChainLength = options.minClusterChainLength;
			Color_Segmenter.majorMinorRatioLimit = options.majorMinorRatioLimit;
			Color_Segmenter.chainJoiningScaler = options.chainJoiningScaler;

			this.printDifferences = false;
		}
		System.out.println("Now about to run!");
		Thread t = new Thread(this);
		t.start();
	}
						
		
	public void run() {
		
		this.sliceClusterMap = new HashMap<Integer, ArrayList<Cluster>>(); //TODO does this need to be synchronised?
		this.pairedClustersBySlice = new HashMap<Integer, HashMap<Cluster, Cluster>>();
		
		this.sliceNumber_clusterValue_clusters_MAP = new HashMap<Integer, HashMap<Integer, ArrayList<Cluster>>>();
		
		ImageStack stack = this.image.getStack(); //this too..?
		int processors = Runtime.getRuntime().availableProcessors();
		float slicesPerThread = (float) stack.getSize() / (float) processors;
		float runningSlicesComputedWithRemainder = 0.01f; //to ensure that you don't have 10,10,10,19 slices for each thread - will be even now.
		//set to 0.01 to avoid 10/3 = 3.333, 3* 3.333 = 9.999 [in which case slice 10 doesn't get used].
		System.out.println(processors + " processors.");
		int startSlice = 1;
		Thread[] threads = new Thread[processors];
		ObjectFinder[] finders = new ObjectFinder[processors];
		for (int i = 0; i < processors; i++) {
			runningSlicesComputedWithRemainder += slicesPerThread;
			int end = (int) runningSlicesComputedWithRemainder;
			ObjectFinder of = new ObjectFinder(startSlice, end, stack, this);
			//System.out.println("Process " + i + " -- " + runningSlicesComputedWithRemainder);
			Thread t = new Thread(of);
			t.start();
			threads[i] = t;
			finders[i] = of;
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
		System.out.println("Finished calculating clusters for each image. Now trying to link them!");
		
		for (int i = 0; i < processors; i++) {
			ObjectFinder of = finders[i];
			for (int sliceNumber = of.start; sliceNumber <= of.end; sliceNumber++) {
				HashMap<Integer, ArrayList<Cluster>> clusterValue_clusters_MAP = of.sliceNumber_clusterValue_clusters_MAP.get(sliceNumber);
				this.sliceNumber_clusterValue_clusters_MAP.put(sliceNumber, clusterValue_clusters_MAP);
			}
		}

		findConnectedClusters();
		//displayClusterPairs();
		findChainedClusters();
		
		findConnectedChains();
		
		highlightChains();
		
		this.limitSelecterFrame.enableRun();
	}
	
	public void findConnectedClusters() {
		HashMap<Cluster, Cluster> connectedClusters;
		
		System.out.println("started findConnectedClusters");
		for (sliceNumber = 1; sliceNumber <= this.image.getStackSize() - 1; sliceNumber++) {
			connectedClusters = new HashMap<Cluster, Cluster>();
			
			//maps for the current slice and for the next slice.
			HashMap<Integer, ArrayList<Cluster>> current_clusterValue_clusters_MAP = sliceNumber_clusterValue_clusters_MAP.get(sliceNumber);
			HashMap<Integer, ArrayList<Cluster>> next_clusterValue_clusters_MAP = sliceNumber_clusterValue_clusters_MAP.get(sliceNumber+1);

			for (int clusterValue = ObjectFinder.rootLowerBound + ObjectFinder.clusterDeviation;
					clusterValue <= ObjectFinder.rootUpperBound - ObjectFinder.clusterDeviation;
							clusterValue++)
			{
				ArrayList<Cluster> clusters1 = current_clusterValue_clusters_MAP.get(clusterValue);
				
				for (Cluster c1: clusters1) {
					boolean isInteresting = false;
					if (this.printMatches) {
						if (sliceNumber >= this.minSliceNumber && sliceNumber <= this.maxSliceNumber) {
							if (c1.center[0] >= this.minCenterX && c1.center[0] <= this.maxCenterX) {
								if (c1.center[1] >= this.minCenterY && c1.center[1] <= this.maxCenterY) {
									if (c1.getArea() >= this.minArea && c1.getArea() <= this.maxArea) {
										if (c1.value >= this.minValue && c1.value <= this.maxValue) {
											isInteresting = true;
											System.out.println("Potential cluster - " + c1);
										}
									}
								}
							}
						}
					}
					boolean printDifferences = isInteresting && this.printDifferences;
					float minDifference = -1;
					Cluster bestCluster = null;
					for (int nextClusterValue = clusterValue - maximumColourDifference; 
								nextClusterValue <= clusterValue + maximumColourDifference; 
										nextClusterValue++) 
					{
						//if this nextClusterValue selected is either too high or too low to match with cluster1..., then continue.
						if (nextClusterValue < ObjectFinder.rootLowerBound+ObjectFinder.clusterDeviation ||
									nextClusterValue > ObjectFinder.rootUpperBound - ObjectFinder.clusterDeviation)
						{
							continue;
						}
						
						ArrayList<Cluster> clusters2 = next_clusterValue_clusters_MAP.get(nextClusterValue);
						
						if (printDifferences) {
							System.out.println("Now considering the cluster\n" + c1);
						}
						
						for (Cluster c2: clusters2) {
							Float difference = compareClusters(c1, c2, printDifferences);
							if (difference == null) {
								//clusters are too far apart to attempt joining...
								continue;
							}
							if (printDifferences) {
								if (c2.getArea() > 50) {
									System.out.println("Difference of " + difference + " with\n" + c2);
								}
							}
							
							if (difference < minDifference || minDifference == -1) {
								//either this has the best difference, or no other cluster set a 'mark' yet.
								minDifference = difference;
								bestCluster = c2;
							}
						}
					}
					
					if (minDifference != -1) {
						//There is some cluster c2 that is the best fit for c1!
						connectedClusters.put(c1, bestCluster);
						if (isInteresting) {
							System.out.println("Have matched up " + c1 + bestCluster);
						}
					}
					else {
						if (isInteresting) {
							System.out.println("Unfortunately no suitable matching clusters were found");
						}
					}
				}
			}
			System.out.println("finished connections starting from slice " + sliceNumber);
			pairedClustersBySlice.put(sliceNumber, connectedClusters);
		}
	}

	//return true if the two clusters are centered close enough to each other to be compared.
	public boolean canCompareClusters(Cluster c1, Cluster c2) {
		if (Math.abs(c1.value-c2.value) > Color_Segmenter.maximumColourDifference) {
			return false;
		}
		float centerXDist = Math.abs(c1.center[0] - c2.center[0]);
		float centerYDist = Math.abs(c1.center[1] - c2.center[1]);

		float centerDifference = (float) Math.sqrt(Math.pow(centerXDist, 2) + Math.pow(centerYDist, 2));

		if (centerDifference > Color_Segmenter.maxCenterDistance) {
			return false;
		}
		//else
		return true;
	}
	
	
	public void displayClusterPairs() {
		//System.out.println(pairedClustersBySlice);
		HashMap<Cluster, Cluster> connectedClusters = pairedClustersBySlice.get(1);
		ImageProcessor sliceOne = this.image.getStack().getProcessor(1);
		ImageProcessor sliceTwo = this.image.getStack().getProcessor(2);
		int count = 0;
		for (Cluster c1: connectedClusters.keySet()) {
			if (!(c1.value == 12 || c1.value == 11)) {
				continue;
			}
			if (Math.abs(c1.center[0] - 115) > 4) {
				continue;
			}
			count++;
			Cluster c2 = connectedClusters.get(c1);
			System.out.println("Pair " + count + " c1Val: " + c1.value + ", c2Val: " + c2.value);
			ImageProcessor newSliceOne = sliceOne.duplicate();
			ImageProcessor newSliceTwo = sliceTwo.duplicate();
			
			int whiteColour = 255;
			byte[] pixels1 = (byte[]) newSliceOne.getPixels();
			byte[] pixels2 = (byte[]) newSliceTwo.getPixels();
			for (Point p : c1.points) {
				pixels1[p.y*newSliceOne.getWidth()+p.x] = (byte) whiteColour;
			}
			for (Point p: c2.points) {
				pixels2[p.y*newSliceTwo.getWidth() + p.x] = (byte) whiteColour;
			}
			newSliceOne.setPixels(pixels1);
			newSliceTwo.setPixels(pixels2);
			
			this.image.getStack().addSlice(newSliceOne);
			this.image.getStack().addSlice(newSliceTwo);
		}
			
		/*HashMap<Cluster, Integer> valueAppearances = new HashMap<Cluster, Integer>();
		for (Cluster key: connectedClusters.keySet()) {
			Cluster value = connectedClusters.get(key);
			if (valueAppearances.keySet().contains(value)) {
				valueAppearances.put(value, valueAppearances.get(value) + 1);
			}
			else {
				valueAppearances.put(value, 1);
			}
		}
		System.out.println(valueAppearances);
		//HashMap<Cluster, Cluster> connectedClusters = pairedClustersBySlice.get(1);
		//ImagePlus ip = this.image.duplicate();*/
		this.image.show();
	}

	public void findChainedClusters() {
		chainLengths_chains_MAP = new HashMap<Integer, ArrayList<ClusterChain>>();

		int stackSize = this.image.getStackSize();
		for (sliceNumber = 1; sliceNumber <= stackSize - Color_Segmenter.minClusterChainLength; sliceNumber++) {
			HashMap<Cluster, Cluster> connectedClusters = pairedClustersBySlice.get(sliceNumber);
			
			Iterator<Map.Entry<Cluster, Cluster>> firstConnectionIterator = connectedClusters.entrySet().iterator();
			ArrayList<Cluster> chain;
			while (firstConnectionIterator.hasNext()) {
				Map.Entry<Cluster, Cluster> firstEntry =  firstConnectionIterator.next();
				Cluster firstKey = firstEntry.getKey();
				Cluster value = firstEntry.getValue();
				Cluster key = firstKey;
				chain = new ArrayList<Cluster>();
				do {
					chain.add(key);
					if (key.getSliceNumber() == stackSize) {
						//there can be no cluster 'value'.
						break;
					}
					if (key.getSliceNumber() == sliceNumber) {
						//this is on the first key, so must be removed using the iterator method.
						firstConnectionIterator.remove();
					}
					else {
						value = pairedClustersBySlice.get(key.getSliceNumber()).remove(key);
					}
					key = value;
				}
				while (key != null);
				
				int chainLength = chain.size();
				if (chainLengths_chains_MAP.containsKey(chainLength)) {
					ArrayList<ClusterChain> chainsAtLength = chainLengths_chains_MAP.get(chainLength);
					chainsAtLength.add(new ClusterChain(chain));
					chainLengths_chains_MAP.put(chainLength, chainsAtLength);
				}
				else {
					ArrayList<ClusterChain> chainsAtLength = new ArrayList<ClusterChain>();
					chainsAtLength.add(new ClusterChain(chain));
					chainLengths_chains_MAP.put(chainLength, chainsAtLength);
				}
			}
		}
		
	}

	public void runPostProcessing(float chainJoiningScaler, float majorMinorRatioLimit, int minClusterChainLength) {
		Runnable r = new Runnable() {
			public void run() {
				Color_Segmenter.minClusterChainLength = minClusterChainLength;
				Color_Segmenter.majorMinorRatioLimit = majorMinorRatioLimit;
				Color_Segmenter.chainJoiningScaler = chainJoiningScaler;
				
				//set 'this.image' to the currently selected image.
				Color_Segmenter.this.updateImage(true);
				//darken the background of the image (by applying the 5-bit lut).
				for (int i = 1; i <= Color_Segmenter.this.image.getStackSize(); i++) {
					ImageProcessor ip = Color_Segmenter.this.image.getStack().getProcessor(i);
					ip.applyTable(Color_Segmenter.lut);
				}
				Color_Segmenter.this.findConnectedChains();
				Color_Segmenter.this.highlightChains();
			}
		};
		Thread t = new Thread(r);
		t.start();
	}
	
	private void highlightChains() {

		//System.out.println("Chain lengths:");
		Iterator<Integer> chainLengthsIterator = chainLengths_chains_MAP.keySet().iterator();
		//System.out.println("Highlighting all chains with length longer than " + Color_Segmenter.minClusterChainLength);

		String lengthsString = "Length: count";
		while(chainLengthsIterator.hasNext()) {
			int nextLength = chainLengthsIterator.next();
			ArrayList<ClusterChain> chains = chainLengths_chains_MAP.get(nextLength);
			lengthsString = lengthsString + ", " + nextLength + ": " + chains.size();
			if (nextLength >= Color_Segmenter.minClusterChainLength) {
				
				for (ClusterChain chain : chains) {
					highlight(chain);
				}
			}
		}
		System.out.println(lengthsString);
		this.duplicateImage.show();
	}
	
	//using Color_Segmenter.majorMinorRatioLimit and Color_Segmenter.chainJoiningScaler --
	//firstly filter to only chains that could be potential roots,
	//then try to join any of these up to other chains (or to other 'root-like chains'.
	//use the chainJoiningScaler as a scaler for the distance away from a root end a root might look.
	//the length of the root chain (derived from major of the ellipse) should also be used 
	private void filterAndJoinChains() {
		
		
	}
	
	//chains should try to join up to other chains (to plug gaps).
	//short chains (more likely to be noise) should not be joined up so easily as long chains to other long chains
	public void findConnectedChains() {
		//for every chain found, fit an ellipse on it to see if it's the right shape.
		//if it has major/minor of larger than 'majorMinorRatioLimit', 
		//use the 'chainJoiningScaler' as a scaler (in some way) [alongside using the major axis as a scaler]
		//to look for other roots that begin along a similar angle to the angle of that ellipse.
		//and connect those chains.
		//if the major/minor is smaller than 'majorMinorRatioLimit',  don't explicitly try to join 
		//this chain up with another chain, but it may be that another chain wants to join up to this one
		//later -- so don't explicitly remove chains that don't fit yet.

		Iterator<Integer> chainLengthIter = chainLengths_chains_MAP.keySet().iterator();
		while(chainLengthIter.hasNext()) {
			int chainLength = chainLengthIter.next();
			if (chainLength >= Color_Segmenter.minClusterChainLength) {
				ArrayList<ClusterChain> chains = chainLengths_chains_MAP.get(chainLength);
				ClusterChain chain = null;
				for (ClusterChain ch: chains) {
					if (ch.clusters.size() >= 8) {
						chain = ch;
						break;
					}
				}
				if (chain == null) {
					continue;
				}
				
				int whiteColour = 255;
				ImagePlus iPlusHighlight = this.image.duplicate();
				for (Cluster c : chain.clusters) {
					
					ImageProcessor sliceProcessor = iPlusHighlight.getStack().getProcessor(c.getSliceNumber());
					byte[] pixels = (byte[]) sliceProcessor.getPixels();
					
					for (Point p : c.points) {
						pixels[p.y*sliceProcessor.getWidth()+p.x] = (byte) whiteColour;
					}
					sliceProcessor.setPixels(pixels);
				}
				iPlusHighlight.setTitle("One Root");
				iPlusHighlight.show();
				ImagePlus ellipseImage = this.image.duplicate();
				Ellipse ell = findEllipse(chain);
				chain.setEllipse(ell);
				System.out.println("Ellipse had radii: " + ell.radiusA + "," + ell.radiusB + "," + ell.radiusC);
				System.out.println("Ellipse had angle: " + ell.angleDegA + "," + ell.angleDegB + "," + ell.angleDegC);
				System.out.println("This gives majorMinorRatio of " + ell.getMajorMinorRatio());
				break;
				





					//if (ell.getMajorMinorRatio() >= Color_Segmenter.majorMinorRatioLimit) {
						//ArrayList<ClusterChain> connectors = findChainsInRange(chain);
						//pick a best one.
						//ClusterChain bestChain = connectors.get(0);

						//chain.append(bestChain);
					//}
				//}
			}
		}
		
		
	}

	public Ellipse findEllipse(ClusterChain chain) {
		//make an ellipse. It should have major and minor axes values, and an angle.
		return new Ellipse(chain);
	}
	public ArrayList<ClusterChain> findChainsInRange(ClusterChain chain) {
		return new ArrayList<ClusterChain>();
	}
		
	
	public float getCenterSmoothness(ClusterChain cc) {
		ArrayList<Cluster> clusters = cc.clusters;
		float[] center;
		float centerDiffTotal = 0;
		float[] initialCenter = clusters.get(0).getCenter();
		float[] finalCenter = clusters.get(clusters.size()-1).getCenter();
		float centerCrowFlys = (float) Math.sqrt(Math.pow(initialCenter[0] - finalCenter[0], 2) + Math.pow(initialCenter[1] - finalCenter[1], 2));
		float[] prevCenter = initialCenter;
		
		for (Cluster c: clusters){ 
			center = c.getCenter();
			float diffCenter = (float) Math.sqrt(Math.pow(center[0] - prevCenter[0], 2) + Math.pow(center[1] - prevCenter[1], 2));
			centerDiffTotal += diffCenter;
			
			prevCenter = center;
		}
		
		float centerMoveAverage = (centerDiffTotal/((float)clusters.size()));
		float centerSmoothness = (centerDiffTotal/centerCrowFlys);
		//System.out.println("Had average: " + centerMoveAverage + ", smoothness: " + centerSmoothness);
		return centerSmoothness;
	}
	
	public float getCenterMovementVariance(ClusterChain cc) {
		ArrayList<Cluster> clusters = cc.clusters;
		float centerDiffTotal = 0;
		float[] center;
		float[] initialCenter = clusters.get(0).getCenter();
		float[] prevCenter = initialCenter;
		for (Cluster c: clusters) {
			center = c.getCenter();
			float diffCenter = (float) Math.sqrt(Math.pow(center[0] - prevCenter[0], 2) + Math.pow(center[1] - prevCenter[1], 2));
			centerDiffTotal += diffCenter;
			prevCenter = center;
		}
		float centerDiffAverage = centerDiffTotal/((float)clusters.size() - 1);
		float centerMovementVariance = 0;
		prevCenter = initialCenter;
		for (Cluster c: clusters) {
			center = c.getCenter();
			float diffCenter = (float) Math.sqrt(Math.pow(center[0] - prevCenter[0], 2) + Math.pow(center[1] - prevCenter[1], 2));

			float variance = (float) Math.pow(diffCenter-centerDiffAverage, 2);
			centerMovementVariance += variance;
			prevCenter = center;
		}
		centerMovementVariance /= (clusters.size()-1);
		return centerMovementVariance;
	}

	public void highlight(ClusterChain chain) {
		ArrayList<Cluster> clusters = chain.clusters;
		//System.out.println("----------------------Highlighting a new chain!-----------------");
		//System.out.println("Length is " + clusters.size() + ", First cluster " + clusters.get(0));
		float centerSmoothness = getCenterSmoothness(chain);
		float centerMovementVariance = getCenterMovementVariance(chain);
		//System.out.println("smoothness: " + centerSmoothness +", movementVariance: " + centerMovementVariance);
		//if (centerSmoothness > 3) {
		//	System.out.println("Not highlighting - smoothnes is too high! " + centerSmoothness);
		//	return;
		//}
		//System.out.println("Will highlight this cluster with smoothness " + centerSmoothness);
		
		int whiteColour = 255;
		
		
		for (Cluster c : clusters) {
			
			ImageProcessor sliceProcessor = this.image.getStack().getProcessor(c.getSliceNumber());
			byte[] pixels = (byte[]) sliceProcessor.getPixels();
			
			for (Point p : c.points) {
				pixels[p.y*sliceProcessor.getWidth()+p.x] = (byte) whiteColour;
			}
			sliceProcessor.setPixels(pixels);
		}
	}
	
	public Float compareClusters(Cluster c1, Cluster c2) {
		return this.compareClusters(c1, c2, false);
	}
	public Float compareClusters(Cluster c1, Cluster c2, boolean isInteresting) {
		//System.out.println(c1.calculatedValues + ", " + c2.calculatedValues);
		if (!(c1.calculatedValues && c2.calculatedValues)) {
			System.out.println("c1: z-"+ c1.z + ", val-" + c1.value + " " + c1.calculatedValues);
			System.out.println("c2: z-"+ c2.z + ", val-" + c2.value + " " + c1.calculatedValues);
			System.out.println("Not both calculated!!");
		}
		float centerXDist = Math.abs(c1.center[0] - c2.center[0]);
		float centerYDist = Math.abs(c1.center[1] - c2.center[1]);

		float centerDifference = (float) Math.sqrt(Math.pow(centerXDist, 2) + Math.pow(centerYDist, 2));

		if (centerDifference > Color_Segmenter.maxCenterDistance) {
			return null;
		}

		float colourDifference = (float) Math.abs(c1.value - c2.value);
		float aspectRatioChange = (float) Math.abs((c1.aspectRatio - c2.aspectRatio)/(c1.aspectRatio + c2.aspectRatio));
		//changed areaDiference -- now divides by c1+c2 -- because otherwise it seems inconsitent TODO.
		float c1Area = (float) c1.area;
		float c2Area = (float) c2.area;
		float areaDifference = (float) Math.abs((c1Area - c2Area)/(c1Area + c2Area));

		
		float comparison = (colourDifference*colourDifferenceWeight) + (aspectRatioChange*aspectRatioDifferenceWeight) + (areaDifference*areaDifferenceWeight);
		if (isInteresting) {
			System.out.println("AreaDiff: " + areaDifference + ", colourDiff: " + colourDifference + ", aspectDiff: " + aspectRatioChange);
			System.out.println("Gives a final score of " + comparison);
		}
		return comparison;
	}

}

class ObjectFinder implements Runnable {
	
	
	//TODO: Do I use neighbours for anything? I don't think I do -- could that be removed?
	
	int start, end;
	ImageStack stack;
	Color_Segmenter cs;
	
	static int rootLowerBound = 8;
	static int rootUpperBound = 25;
	static int clusterDeviation = 1;
	int count;
	int xMin, yMin, xMax, yMax;
	int X,Y;
	
	ArrayList<Point> newClusterToBeProcessed;
	ArrayList<Point> sameClusterToBeProcessed;
	HashMap<Integer, ArrayList<Cluster>>  clusterValue_clusters_MAP;
	HashMap<Integer, HashMap<Integer, ArrayList<Cluster>>> sliceNumber_clusterValue_clusters_MAP;
	Point[][] points;
	ArrayList<Point> pointsAtValue;
	ArrayList<Cluster> clustersAtValue;
	HashSet<Point> processed;
	
	int sliceNumber;
	
	public ObjectFinder(int start, int end, ImageStack stack, Color_Segmenter cs) {
		this.start = start;
		this.end = end;
		this.stack = stack;
		this.cs = cs;
		this.sliceNumber_clusterValue_clusters_MAP = new HashMap<Integer, HashMap<Integer, ArrayList<Cluster>>>();
		this.count = 0;
		xMin = 0;
		yMin = 0;
		xMax = this.cs.X - 1;
		yMax = this.cs.Y - 1;
		this.X = this.cs.X;
		this.Y = this.cs.Y;

	}
	
	public void run() {
		System.out.println("Thread between " + start + "-" + end + " has begun.");
		if (start > end) {
			System.out.println("Object finder ended - no slices to process!");
			return;
		}
		for (sliceNumber = start; sliceNumber <= end; sliceNumber++) {
			System.out.println("Run on Slice: " + sliceNumber);
			ImageProcessor nextSlice = stack.getProcessor(sliceNumber);
			convertToBins(nextSlice);
			connectivityAnalysis(nextSlice);
			sliceNumber_clusterValue_clusters_MAP.put(sliceNumber, clusterValue_clusters_MAP);
		}
		findChains();
	}
	
	public void convertToBins(ImageProcessor ip) {
		//copying some lines from the threshold code (https://imagej.nih.gov/ij/source/ij/plugin/Thresholder.java)
		ip.applyTable(Color_Segmenter.lut);
	}
	
	public ImageProcessor highlightClusters(ImageProcessor ip, ArrayList<Cluster> clusters) {
		int pointCount = 0;
		for (Cluster c: clusters) {
			pointCount += c.getArea();
		}
		int[] xPoints = new int[pointCount];
		int[] yPoints = new int[pointCount];
		
		int pointIndex = 0;
		for (Cluster c: clusters) {
			for (Point p: c.points) {
				xPoints[pointIndex] = p.x;
				yPoints[pointIndex] = p.y;
				pointIndex++;
			}
		}
		//PolygonRoi initialClustersRoi = new PolygonRoi(xPoints, yPoints, pointCount, Roi.POLYGON);
		int width = this.X;
		byte[] pixelsCopy = (byte[]) ip.getPixelsCopy();
		byte whiteColour = (byte) 255;
		byte blackColour = (byte) 0;
		int totalArea = this.X * this.Y;
		for (int i = 0; i < totalArea; i++) {
			pixelsCopy[i] = blackColour;
		}
		
		for (int i = 0; i < pointCount; i++) {
			pixelsCopy[yPoints[i]*width + xPoints[i]] = whiteColour;
		}
		

		ImageProcessor processClusters = ip.duplicate();
		processClusters.setPixels(pixelsCopy);
		return processClusters;
	}
		
		
		
	//pixelValue and clusterValue need to be different:
	//First go: pixelValue == clusterValue -- look for clusters around pixelValue, create clusters of clusterValue.
	//Second go: pixelValue = 0, clusterValue is the same as before. Look for clusters at val 0, but create clusters of clusterValue.
	
	//need to find a way to make sure looking for clusters around pixel-Value0 doesn't pick up pixel-value 1.
	//Unless pixel-value 0 is impossible.... [due to original range as 0-31], then invert so 1 == old 254 (which can't exist).
	//I think that is the case actually.
	public void findClusters(ImageProcessor ip, int clusterValue) {
		findClusters(ip, clusterValue, clusterValue);	
	}
	public void findClusters(ImageProcessor ip, int pixelValue, int clusterValue) {
		//if (sliceNumber == 1) {
		//	System.out.println("Running findClusters on " + pixelValue);
		//	for (int i = 0; i < 20; i++) {
		//		System.out.println();
		//	}
		//}
		points = new Point[this.X][this.Y];
		for (int i = 0; i <= xMax; i++) {
			for (int j = 0; j <= yMax; j++) {
				points[i][j] = new Point(i, j, ip.get(i, j));
			}
		}
		
		clustersAtValue = new ArrayList<Cluster>();
		pointsAtValue = new ArrayList<Point>();
		int count = 0;
		for (int i = 0; i <= xMax; i++) {
			for (int j = 0; j <= yMax; j++) {
				if (points[i][j].value == pixelValue) {
					pointsAtValue.add(points[i][j]);
				}
			}
		}
		
		processed = new HashSet<Point>();		
		while(pointsAtValue.size() != 0) {
			Point nextPoint = pointsAtValue.remove(0);
			//if (sliceNumber == 1 && pixelValue == 3) {
			//	System.out.println("Slice: " + sliceNumber + ", Looking at point " + nextPoint);
			//}
			if (processed.contains(nextPoint)) {
				System.out.println(nextPoint + " has already been processed!");
				continue;
			}
			Cluster currentCluster = new Cluster(nextPoint, sliceNumber);
			currentCluster.setValue(clusterValue);
			//if (sliceNumber == 1) {
			//	System.out.println("new cluster at point " + nextPoint + " at cluster value: " + clusterValue + ", point value is " + nextPoint.value);
			//}
			sameClusterToBeProcessed = new ArrayList<Point>();
			sameClusterToBeProcessed.add(nextPoint);
			processed.add(nextPoint);
			
			
			while(!sameClusterToBeProcessed.isEmpty()) {
				nextPoint = sameClusterToBeProcessed.remove(0); //the value at index 0 is returned while being removed.
				if (nextPoint.x - 1 >= xMin) {
					considerPoint(nextPoint.x - 1, nextPoint.y, currentCluster, pixelValue);
				}
				
				if (nextPoint.x + 1 <= xMax) {
					considerPoint(nextPoint.x + 1, nextPoint.y, currentCluster, pixelValue);
				}
				
				if (nextPoint.y - 1 >= yMin) {
					considerPoint(nextPoint.x, nextPoint.y - 1, currentCluster, pixelValue);
				}
				
				if (nextPoint.y + 1 <= yMax) {
					considerPoint(nextPoint.x, nextPoint.y + 1, currentCluster, pixelValue);
				}
				
			}
			//System.out.println("sameClusterToBeProcessed is empty!");
			clustersAtValue.add(currentCluster);
			//if (sliceNumber == 1 && pixelValue == 3) {
			//	System.out.println("Slice: " + sliceNumber + ", size was " + currentCluster.getArea() + ", initialPoint was " + currentCluster.points.get(0));
			//	
			//	try {
			//		Thread.sleep(1000);
			//	} catch (Exception e) {}
			//}
			//if (sliceNumber == 1) {
			//	System.out.println("Cluster added with size " + currentCluster.getArea());
			//}
		}
		//if (sliceNumber ==1) {
		//	System.out.println("Finished finding clusters at value " + pixelValue);
		//}
			
	}
	
	public void considerPoint(int x, int y, Cluster c, int clusterCentralPixelValue) {
		Point newPoint = points[x][y];
		if (processed.contains(newPoint)) {
			//if (sliceNumber ==1) {
				//System.out.println("Not considering point " + newPoint + " because it is already processed.");
			//}
			return;
		}
		int valueDifference = Math.abs(newPoint.value - clusterCentralPixelValue);
		count++;
		//if (count % 10000 == 0) {
		//if (sliceNumber ==1) {
			//System.out.println("Considering " + newPoint + "valueDifference is " + newPoint.value + " - " + c.value + " = " + (newPoint.value-c.value));
		//}
		//}
		if (valueDifference <= ObjectFinder.clusterDeviation) {
			sameClusterToBeProcessed.add(newPoint);
			processed.add(newPoint);
			//if (sliceNumber == 1) {
			//	System.out.println("Adding " + newPoint);
			//}
			if (valueDifference == 0) {
				pointsAtValue.remove(newPoint);
				c.addPoint(newPoint, true);
			}
			else {
				c.addPoint(newPoint, false);
			}
		}

	}
	
	
	//an entirely different implementation...
	public void connectivityAnalysis(ImageProcessor ip) {
		

		clusterValue_clusters_MAP = new HashMap<Integer, ArrayList<Cluster>>();
		
		for (int clusterValue = ObjectFinder.rootLowerBound + ObjectFinder.clusterDeviation;
					clusterValue <= ObjectFinder.rootUpperBound - ObjectFinder.clusterDeviation; clusterValue++)
		{
			findClusters(ip, clusterValue);
			
			//at this stage all the clusters at this value have been performed.
			//We know want to compute on these -- (Median, Dilate, Fill-holes, erode, watershed) to smooth out and join up close clusters.
			
			ImageProcessor highlightedClusters = highlightClusters(ip, clustersAtValue);
			
			ImagePlus imp = new ImagePlus("Display Processed Clusters" + clusterValue, highlightedClusters);
			
			IJ.run(imp, "Median...", "2");
			IJ.run(imp, "Invert", "");
			IJ.run(imp, "Dilate", "");
			IJ.run(imp, "Fill Holes", "");
			IJ.run(imp, "Erode", "");
			IJ.run(imp, "Watershed", "");
			
			//if (sliceNumber == 1) {
				//imp.show();
			//}
			
			//TODO: Find the code implementation of watershed -- see if it is possible to convert the resulting data structures into
			//the new cluster list. Do I need to do that?? This seems not too inefficient for now...
			
			findClusters(highlightedClusters, 0, clusterValue);
			
			ArrayList<Cluster> largeClusters = new ArrayList<Cluster>();
			for (Cluster c: clustersAtValue) {
				if (c.getArea() >= Color_Segmenter.minClusterSize) {
					c.setValue(clusterValue);
					largeClusters.add(c);
					c.postProcessing();
				}
			}
			clusterValue_clusters_MAP.put(clusterValue, largeClusters);
		}
			
		
	}
	
	
	
	//not implemented at this stage (while still divided into multiple threads) yet.
	public void findChains() {
		
		//System.err.println("findChains is not implemented yet!!");
		System.err.println("Finished finding clusters from slices: " + start + "-" + end);
	}
		
}

class LimitSelecterFrame extends JFrame {
	Color_Segmenter cs;
	JButton run;
	JButton postProcessing;
	JTextField rootLowerBound;
	JTextField rootUpperBound;
	JCheckBox printMatches;
	JCheckBox printDifferences;
	
	JTextField minSliceNumber;
	JTextField maxSliceNumber;
	JTextField minValue;
	JTextField maxValue;
	JTextField minCenterX;
	JTextField minCenterY;
	JTextField maxCenterX;
	JTextField maxCenterY;
	JTextField minArea, maxArea;
	JTextField chainJoiningScaler;
	JTextField majorMinorRatioLimit;

	JTextField colourDifferenceWeight, areaDifferenceWeight, aspectRatioDifferenceWeight;
	JTextField minClusterSize, minClusterChainLength, maxCenterDistance;
	
	public LimitSelecterFrame(Color_Segmenter cs) {
		this.cs = cs;
		
		this.setTitle("Select limits");
		this.setSize(550,750);
		this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		this.setLayout(new FlowLayout());
		
		this.add(new JLabel("Root lower bound (5 bit)"));
		this.rootLowerBound = new JTextField("", 3);
		this.add(rootLowerBound);
		
		this.add(new JLabel("Root upper bound (5 bit)"));
		this.rootUpperBound = new JTextField("", 3);
		this.add(rootUpperBound);
		
		this.printMatches = new JCheckBox("Print matches?", false);
		this.add(printMatches);
		this.printDifferences = new JCheckBox("Print differences?", false);
		this.printDifferences.setEnabled(false);
		this.add(printDifferences);
		
		JPanel limitPanel = new JPanel();
		limitPanel.setLayout(new BoxLayout(limitPanel, BoxLayout.Y_AXIS));
		this.add(limitPanel);
		
	
		limitPanel.add(new JLabel("min slice number"));
		this.minSliceNumber = new JTextField("", 3);
		this.minSliceNumber.setEnabled(false);
		limitPanel.add(minSliceNumber);
		
		limitPanel.add(new JLabel("max slice number"));
		this.maxSliceNumber = new JTextField("", 3);
		this.maxSliceNumber.setEnabled(false);
		limitPanel.add(maxSliceNumber);
		
		limitPanel.add(new JLabel("min value"));
		this.minValue = new JTextField("", 3);
		this.minValue.setEnabled(false);
		limitPanel.add(minValue);
		
		limitPanel.add(new JLabel("maxValue"));
		this.maxValue = new JTextField("", 3);
		this.maxValue.setEnabled(false);
		limitPanel.add(maxValue);
		
		limitPanel.add(new JLabel("minArea"));
		this.minArea = new JTextField("", 3);
		this.minArea.setEnabled(false);
		limitPanel.add(minArea);
		
		limitPanel.add(new JLabel("maxArea"));
		this.maxArea = new JTextField("", 3);
		this.maxArea.setEnabled(false);
		limitPanel.add(maxArea);
		
		limitPanel.add(new JLabel("minCenterX"));
		this.minCenterX = new JTextField("", 3);
		this.minCenterX.setEnabled(false);
		limitPanel.add(minCenterX);
		
		limitPanel.add(new JLabel("maxCenterX"));
		this.maxCenterX = new JTextField("", 3);
		this.maxCenterX.setEnabled(false);
		limitPanel.add(maxCenterX);
		
		limitPanel.add(new JLabel("minCenterY"));
		this.minCenterY = new JTextField("", 3);
		this.minCenterY.setEnabled(false);
		limitPanel.add(minCenterY);
		
		limitPanel.add(new JLabel("maxCenterY"));
		this.maxCenterY = new JTextField("", 3);
		this.maxCenterY.setEnabled(false);
		limitPanel.add(maxCenterY);
		
		this.run = new JButton("RUN");

		this.run.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				System.out.println("Run action performed");
				int rootLowerBound = Integer.parseInt(LimitSelecterFrame.this.rootLowerBound.getText());
				int rootUpperBound = Integer.parseInt(LimitSelecterFrame.this.rootUpperBound.getText());
				boolean printMatches = LimitSelecterFrame.this.printMatches.isSelected();
				int minSliceNumber, maxSliceNumber, minValue, maxValue;
				int minArea, maxArea, minCenterX, maxCenterX, minCenterY, maxCenterY;
				boolean printDifferences;

				int minClusterSize = Integer.parseInt(LimitSelecterFrame.this.minClusterSize.getText());
				int minClusterChainLength = Integer.parseInt(LimitSelecterFrame.this.minClusterChainLength.getText());
				float majorMinorRatioLimit = Float.parseFloat(LimitSelecterFrame.this.majorMinorRatioLimit.getText());
				float chainJoiningScaler = Float.parseFloat(LimitSelecterFrame.this.chainJoiningScaler.getText());
				int maxCenterDistance = Integer.parseInt(LimitSelecterFrame.this.maxCenterDistance.getText());
				float areaDifferenceWeight = Float.parseFloat(LimitSelecterFrame.this.areaDifferenceWeight.getText());
				float colourDifferenceWeight = Float.parseFloat(LimitSelecterFrame.this.colourDifferenceWeight.getText());
				float aspectRatioDifferenceWeight = Float.parseFloat(LimitSelecterFrame.this.aspectRatioDifferenceWeight.getText());

				LimitSelecterFrame.this.run.setEnabled(false);
				LimitSelecterFrame.this.postProcessing.setEnabled(false);

				if (printMatches) {


					minSliceNumber = Integer.parseInt(LimitSelecterFrame.this.minSliceNumber.getText());
					maxSliceNumber = Integer.parseInt(LimitSelecterFrame.this.maxSliceNumber.getText());
					minValue = Integer.parseInt(LimitSelecterFrame.this.minValue.getText());
					maxValue = Integer.parseInt(LimitSelecterFrame.this.maxValue.getText());
					minArea = Integer.parseInt(LimitSelecterFrame.this.minArea.getText());
					maxArea = Integer.parseInt(LimitSelecterFrame.this.maxArea.getText());
					minCenterX = Integer.parseInt(LimitSelecterFrame.this.minCenterX.getText());
					maxCenterX = Integer.parseInt(LimitSelecterFrame.this.maxCenterX.getText());
					minCenterY = Integer.parseInt(LimitSelecterFrame.this.minCenterY.getText());
					maxCenterY = Integer.parseInt(LimitSelecterFrame.this.maxCenterY.getText());
					printDifferences = LimitSelecterFrame.this.printDifferences.isSelected();

					ColorBlobOptions optionsWithLimits = new ColorBlobOptions(rootLowerBound, rootUpperBound, printMatches,
						minClusterSize, maxCenterDistance, areaDifferenceWeight, aspectRatioDifferenceWeight,
						colourDifferenceWeight, minClusterChainLength, majorMinorRatioLimit, chainJoiningScaler,
						minSliceNumber, maxSliceNumber, minValue, maxValue, minArea, maxArea,
						minCenterX, maxCenterX, minCenterY, maxCenterY, printDifferences);
					System.out.println("It was true, now about to run.");
					LimitSelecterFrame.this.cs.run(optionsWithLimits);
				}				
				else {

					ColorBlobOptions optionsNoLimits = new ColorBlobOptions(rootLowerBound, rootUpperBound, printMatches,
						minClusterSize, maxCenterDistance, areaDifferenceWeight, aspectRatioDifferenceWeight,
						colourDifferenceWeight, minClusterChainLength, majorMinorRatioLimit, chainJoiningScaler);
					System.out.println("It was false, now about to run");
					LimitSelecterFrame.this.cs.run(optionsNoLimits);
				}
				System.out.println("done in actionListener");
			}
		});

		this.add(run);

		
		this.printMatches.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				System.out.println("Toggling boxes.");
				boolean selected = LimitSelecterFrame.this.printMatches.isSelected();
				LimitSelecterFrame.this.minSliceNumber.setEnabled(selected);
				LimitSelecterFrame.this.maxSliceNumber.setEnabled(selected);
				LimitSelecterFrame.this.minValue.setEnabled(selected);
				LimitSelecterFrame.this.maxValue.setEnabled(selected);
				LimitSelecterFrame.this.minArea.setEnabled(selected);
				LimitSelecterFrame.this.maxArea.setEnabled(selected);
				LimitSelecterFrame.this.minCenterX.setEnabled(selected);
				LimitSelecterFrame.this.maxCenterX.setEnabled(selected);
				LimitSelecterFrame.this.minCenterY.setEnabled(selected);
				LimitSelecterFrame.this.maxCenterY.setEnabled(selected);
				LimitSelecterFrame.this.printDifferences.setEnabled(selected);
			}
		});
		
		JPanel constantsPanel = new JPanel();
		constantsPanel.setLayout(new BoxLayout(constantsPanel, BoxLayout.Y_AXIS));
		this.add(constantsPanel);


		constantsPanel.add(new JLabel("minClusterSize"));
		this.minClusterSize = new JTextField("20", 5);
		constantsPanel.add(minClusterSize);
		
		constantsPanel.add(new JLabel("maxCenterDistance"));
		this.maxCenterDistance = new JTextField("5", 5);
		constantsPanel.add(maxCenterDistance);

		constantsPanel.add(new JLabel("areaDifferenceWeight"));
		this.areaDifferenceWeight = new JTextField("5.0", 5);
		constantsPanel.add(areaDifferenceWeight);

		constantsPanel.add(new JLabel("colourDifferenceWeight"));
		this.colourDifferenceWeight = new JTextField("0.1", 5);
		constantsPanel.add(colourDifferenceWeight);

		constantsPanel.add(new JLabel("aspectRatioDifferenceWeight"));
		this.aspectRatioDifferenceWeight = new JTextField("1.0", 5);
		constantsPanel.add(aspectRatioDifferenceWeight);



		
		JPanel postProcessPanel = new JPanel();
		postProcessPanel.setLayout(new BoxLayout(postProcessPanel, BoxLayout.Y_AXIS));
		this.add(postProcessPanel);
		postProcessPanel.add(new JLabel("Modify post-processing constants"));
		
		postProcessPanel.add(new JLabel("minClusterChainLength"));
		this.minClusterChainLength = new JTextField("15", 5);
		postProcessPanel.add(minClusterChainLength);
		
		postProcessPanel.add(new JLabel("chainJoiningScaler"));
		this.chainJoiningScaler = new JTextField("1", 5);
		postProcessPanel.add(chainJoiningScaler);
		
		postProcessPanel.add(new JLabel("majorMinorRatioLimit"));
		this.majorMinorRatioLimit = new JTextField("2", 5);
		postProcessPanel.add(majorMinorRatioLimit);
		
		this.postProcessing = new JButton("Run Post-Processing only");
		postProcessPanel.add(postProcessing);
		this.postProcessing.setEnabled(false);
		
		this.postProcessing.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int minClusterChainLength = Integer.parseInt(LimitSelecterFrame.this.minClusterChainLength.getText());
				float majorMinorRatioLimit = Float.parseFloat(LimitSelecterFrame.this.majorMinorRatioLimit.getText());
				float chainJoiningScaler = Float.parseFloat(LimitSelecterFrame.this.chainJoiningScaler.getText());
				LimitSelecterFrame.this.cs.runPostProcessing(chainJoiningScaler, majorMinorRatioLimit, minClusterChainLength);
			}
		});
		
		this.setVisible(true);
		System.out.println("Made it visible!");
	}

	public void enableRun() {
		this.run.setEnabled(true);
		this.postProcessing.setEnabled(true);
	}
}


