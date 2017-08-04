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

import org.apache.commons.collections4.bidimap.*;

import java.util.*;

public class Color_Segmenter extends SegmentationPlugin implements PlugInFilter {
	


	static int[] lut;
	static int minClusterSize = 20;

	static float colourDifferenceWeight = 0.1f;
	static float areaDifferenceWeight = 5.0f;
	static float aspectRatioDifferenceWeight = 1.0f;
	static float maxCenterDistance = 5;
	static int minClusterChainLength = 15;
	
	static int maximumColourDifference = 1; //no of bins apart.
	
	static {
		int binsRequired = 32;
		float divisor = (float)256/(float) binsRequired;
		lut = new int[256];
		for (int i = 0; i< 256; i++) {
			lut[i] = (int) ((float)i/divisor);
		}
	}
	

	HashMap<Integer, ArrayList<Cluster>> sliceClusterMap;
	HashMap<Integer, Integer> differenceCounts;
	HashMap<Integer, DualHashBidiMap<Cluster, Cluster>> pairedClustersBySlice;
	HashMap<Cluster, Cluster> replacements;
	
	HashMap<Integer, HashMap<Integer, ArrayList<Cluster>>> sliceNumber_clusterValue_clusters_MAP;


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
		this.pairedClustersBySlice = new HashMap<Integer, DualHashBidiMap<Cluster, Cluster>>();
		
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
		displayClusterPairs();
		//findChainedClusters();
	}
	
	public void findConnectedClusters() {
		DualHashBidiMap<Cluster, Cluster> connectedClusters;
		System.out.println("started findConnectedClusters");
		for (int sliceNumber = 1; sliceNumber <= this.image.getStackSize() - 1; sliceNumber++) {
			connectedClusters = new DualHashBidiMap<Cluster, Cluster>();
			
			for (int clusterValue = ObjectFinder.rootLowerBound + ObjectFinder.clusterDeviation;
						clusterValue < ObjectFinder.rootUpperBound - ObjectFinder.clusterDeviation; 
								clusterValue++) 
			{
				HashMap<Integer, ArrayList<Cluster>> current_clusterValue_clusters_MAP = sliceNumber_clusterValue_clusters_MAP.get(sliceNumber);
				HashMap<Integer, ArrayList<Cluster>> next_clusterValue_clusters_MAP = sliceNumber_clusterValue_clusters_MAP.get(sliceNumber+1);
				ArrayList<Cluster> clusters1 = current_clusterValue_clusters_MAP.get(clusterValue);
				
				for (Cluster c1: clusters1) {
					float minDifference = -1;
					Cluster bestCluster = null;
					for (int nextClusterValue = clusterValue - maximumColourDifference; 
								nextClusterValue <= clusterValue + maximumColourDifference; 
										nextClusterValue++) 
					{
						if (nextClusterValue < ObjectFinder.rootLowerBound+ObjectFinder.clusterDeviation ||
									nextClusterValue > ObjectFinder.rootUpperBound - ObjectFinder.clusterDeviation)
						{
							//System.out.println("No clusters at value " + nextClusterValue + " - continuing");
							continue;
						}
						//System.out.println("HERE - where that NULLPOINTER was!!!");
						ArrayList<Cluster> clusters2 = next_clusterValue_clusters_MAP.get(nextClusterValue);
						//System.out.println("Just starting out here, nextClusterValue will be " + nextClusterValue);
						//System.out.println(next_clusterValue_clusters_MAP);
						if (clusters2.isEmpty()) {
							System.out.println(sliceNumber + ", " + nextClusterValue + " has empty clusters2");
						}
						for (Cluster c2 : clusters2) {
							Float difference = compareClusters(c1, c2);
							if (difference == null) {
								//clusters are too far apart to attempt joining...
								continue;
							}
							
							if (difference < minDifference || minDifference == -1) {
								minDifference = difference;
								bestCluster = c2;
							}
						}
					}
					
					if (minDifference != -1) {
						
						if (connectedClusters.containsValue(bestCluster)) {
							//then some other cluster also mapped to best cluster. This can't be allowed.
							Cluster otherC1 = connectedClusters.getKey(bestCluster);
							Float c1DiffBest = minDifference;
							Float otherC1DiffBest = compareClusters(otherC1, bestCluster);
							if (c1DiffBest < otherC1DiffBest) {
								//c1 is the best match for bestCluster --
								//remove the old association, add the new association, and prepare the 'back-propagation' of this change.
								connectedClusters.remove(otherC1);
								connectedClusters.put(c1, bestCluster);
								prepareToBackPropagate(c1, otherC1);
							}
							else {
								//otherC1 is the best match for bestCluster.
								prepareToBackPropagate(otherC1, c1);
							}
						}
						else {
							//simple case
							connectedClusters.put(c1, bestCluster);
						}
					}
						
				}
			}
			System.out.println("finished connections starting from slice " + sliceNumber);
			pairedClustersBySlice.put(sliceNumber, connectedClusters);
			backPropagate(sliceNumber);
		}
	}
	

	
	public void prepareToBackPropagate(Cluster replacement, Cluster replaced) {
		if (this.replacements == null) {
			this.replacements = new HashMap<Cluster, Cluster>();
		}
		this.replacements.put(replaced, replacement);
	}
	
	public void backPropagate(int startSlice) {
		//if there si nothing to backPropagate, then does nothing.
		if (this.replacements.size() == 0) {
			System.out.println("Finished back propagating -- nothing to propagate back");
			return;
		}
		
		//if looking at slice 1, then no 'prevConnectedClusters' will remain -- need to sort this out separately.
		if (startSlice == 1) { 
			//there ought to be nothing to do.
			System.out.println("Finished back propagating due to reaching slice 1");
			
			return;
		}
		//otherwise
		DualHashBidiMap<Cluster, Cluster> connectedClusters = pairedClustersBySlice.get(startSlice);
		DualHashBidiMap<Cluster, Cluster> prevConnectedClusters = pairedClustersBySlice.get(startSlice - 1);
		
		
		Iterator<Cluster> it = this.replacements.keySet().iterator();
		while(it.hasNext()) {
			Cluster replaced = it.next();
			Cluster replacement = this.replacements.get(replaced);
			
			//what if A replaces C, but D then replaces A? 
			while (this.replacements.containsKey(replacement)) {
				replacement = this.replacements.get(replacement);
			}
			
			//TODO fix the iterator (to remove items).
			
			
			//if 'replacement' replaces 'replaced', fancy back-propagation is only needed when:
			//('preExistingReplacedKey'->'replaced') exists in the prevConnectedClusters.
			if (prevConnectedClusters.containsValue(replaced)) {
				Cluster preExistingReplacedKey = prevConnectedClusters.getKey(replaced);
				
				//I want to set (preExistingReplacedKey -> replacement) as a pair [but can only do this if they meet certain conditions].
				if(canCompareClusters(preExistingReplacedKey, replacement)) {
					//It's possible that replacement already had a different key [another conflict would arise].
					if (prevConnectedClusters.containsValue(replacement)) {
						Cluster preExistingReplacementKey = prevConnectedClusters.getKey(replacement);
						//then we will need to backPropagate further.
						//Only one of [(preExistingReplacedKey -> replacement) & (preExistingReplacementKey -> replacement)] can be used.
						Float replacedKeyDiff = compareClusters(preExistingReplacedKey, replacement);
						Float replacementKeyDiff = compareClusters(preExistingReplacementKey, replacement);
						
						if (replacedKeyDiff < replacementKeyDiff) {
							//replacedKeyDiff is the best match for replacement.
							//need to modify the prevConnectedClusters to reflect this, then propagate it back.
							//prevConnectedClusters started with (preExistingReplacementKey -> replacement), 
							//now needs to have (preExistingReplacedKey -> replacement), 
							//and any occurences of 'preExistingReplacementKey' in the prevPrevConnectedClusters would need to be changed.
							prevConnectedClusters.remove(preExistingReplacementKey);
							prevConnectedClusters.put(preExistingReplacedKey, replacement);
							prepareToBackPropagate(preExistingReplacedKey, preExistingReplacementKey);
						}
						else {
							//replacementKeyDiff is still the best match for replacement.
							//so no immediate changes to make here.
							//need to backPropagate in case some (F->preExistingReplacedKey) exists that might need to be updated to:
							//(F->preExistingReplacementKey)  [[in order to keep that chain going]].
							prepareToBackPropagate(preExistingReplacementKey, preExistingReplacedKey);
						}
					}
					else {
						//if not, then we can end this chain of changes here.
						//replace (preExistingReplacedKey -> replaced) with (preExistingReplacedKey -> replacement)
						prevConnectedClusters.put(preExistingReplacedKey, replacement);
					}
					
				}
				else {
					//if preExistingReplacedKey can't link to replacement, then nothing need be changed/
					//(preExistingReplacedKey -> replaced) will remain in prevConnectedClusters.
				}
			}
			
			else {
				//there is nothing to be done.
			}
		}
		backPropagate(startSlice-1);
	}
	
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
		DualHashBidiMap<Cluster, Cluster> connectedClusters = pairedClustersBySlice.get(1);
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
	
	/*public void findChainedClusters() {
		HashMap<Cluster, Cluster> connectedClusters = pairedClustersBySlice.get(1);
		for (Cluster c: connectedClusters.keySet()) {
		}
	}*/
			
			
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
		
		System.out.println("begin findChainedClusters()");
		for (int sliceNumber = 1; sliceNumber <= this.image.getStackSize() - 1; sliceNumber++) {
			DualHashBidiMap<Cluster, Cluster> connectedClusters = pairedClustersBySlice.get(sliceNumber);
			ArrayList<Cluster> valuesList = new ArrayList<Cluster>(connectedClusters.values());
			Set<Cluster> valuesSet = new HashSet<Cluster>(connectedClusters.values());
			//System.out.println("valuesList " + valuesList.size());
			//System.out.println("valuesSet " + valuesSet.size());
			if (valuesList.size() != valuesSet.size()) {
				System.out.println("On slice " + sliceNumber + ", valuesList: " + valuesList.size() + ", valuesSet: " + valuesSet.size());
			}
		}
		System.out.println("finished findChainedClusters");
		
		/*HashMap<Integer, Integer> clusterLengths = new HashMap<Integer, Integer>();
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
					//System.out.println("Cluster " + firstKey + " had a chainLength of " + length);
				}
				if (clusterLengths.containsKey(length)) {
					clusterLengths.put(length, clusterLengths.get(length) + 1);
				}
				else {
					clusterLengths.put(length, 1);
				}
				if (length > minClusterChainLength) {
					System.out.println(firstKey + " has a length of at least " + length);
					ArrayList<Cluster> toBeDisplayed = new ArrayList<Cluster>();
					key = firstKey;
					do {
						toBeDisplayed.add(key);
						if (key.getSliceNumber() == stackSize) {
							break;
						}
						next = pairedClustersBySlice.get(key.getSliceNumber()).get(key);
						key = next;
					}
					while (key != null);
					highlight(toBeDisplayed);
				}
			}
		}
		
		System.out.println("Cluster lengths: " + clusterLengths);*/
	}

	public void highlight(ArrayList<Cluster> clusters) {
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

		float areaDifference = (float) Math.abs((c1.area - c2.area)/(c1.area + c2.area));

		float comparison = colourDifference*colourDifferenceWeight + aspectRatioChange*aspectRatioDifferenceWeight + areaDifference*areaDifferenceWeight;
		return comparison;
	}

}

class ObjectFinder implements Runnable {
	
	
	//TODO: Do I use neighbours for anything? I don't think I do -- could that be removed?
	
	int start, end;
	ImageStack stack;
	Color_Segmenter cs;
	
	static int rootLowerBound = 8; //so 4-5-6 is allowed
	static int rootUpperBound = 26; //so 11-12-13 is allowed.
	static int clusterDeviation = 1;
	int count;
	
	ArrayList<Point> newClusterToBeProcessed;
	ArrayList<Point> sameClusterToBeProcessed;
	HashMap<Integer, ArrayList<Cluster>>  clusterValue_clusters_MAP;
	HashMap<Integer, HashMap<Integer, ArrayList<Cluster>>> sliceNumber_clusterValue_clusters_MAP;
	Point[][] points;
	
	int sliceNumber;
	
	public ObjectFinder(int start, int end, ImageStack stack, Color_Segmenter cs) {
		this.start = start;
		this.end = end;
		this.stack = stack;
		this.cs = cs;
		this.sliceNumber_clusterValue_clusters_MAP = new HashMap<Integer, HashMap<Integer, ArrayList<Cluster>>>();
		this.count = 0;
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
			convertTo4Bit(nextSlice);
			connectivityAnalysis(nextSlice);
			//cs.sliceClusterMap.put(sliceNumber, largeClusters);
		}
		findChains();
	}
	
	public void convertTo4Bit(ImageProcessor ip) {
		//copying some lines from the threshold code (https://imagej.nih.gov/ij/source/ij/plugin/Thresholder.java)
		ip.applyTable(Color_Segmenter.lut);
	}
	
	public void connectivityAnalysis(ImageProcessor ip) {
		
		//System.out.println("Begin connectivityAnalysis on " + sliceNumber);
		int xMin = 0;
		int yMin = 0;
		int xMax = this.cs.X - 1;
		int yMax = this.cs.Y - 1;
		
		
		newClusterToBeProcessed = new ArrayList<Point>();
		sameClusterToBeProcessed = new ArrayList<Point>();
		clusterValue_clusters_MAP = new HashMap<Integer, ArrayList<Cluster>>();
		int largeClusters = 0;
		
		for (int i = ObjectFinder.rootLowerBound + ObjectFinder.clusterDeviation;
					i <= ObjectFinder.rootUpperBound - ObjectFinder.clusterDeviation; i++) 
		{
			clusterValue_clusters_MAP.put(i, new ArrayList<Cluster>());
		}
		
		points = new Point[xMax+1][yMax+1];
		for (int i = 0; i < xMax+1; i++) {
			for (int j = 0; j < yMax+1; j++) {
				points[i][j] = new Point(i, j, ip.get(i, j));
			}
		}
		
		addToNewClusterList(points[0][0]);
		
		Cluster currentCluster = null;
		
		
		while(!(newClusterToBeProcessed.isEmpty() && sameClusterToBeProcessed.isEmpty())) {
			count++;
			Point nextPoint;
			boolean fromSameCluster;
			if (!sameClusterToBeProcessed.isEmpty()) {
				nextPoint = sameClusterToBeProcessed.get(0);
				fromSameCluster = true;
			}
			else {
				if (currentCluster != null) {
					//finalise the cluster --
					if (currentCluster.getArea() > Color_Segmenter.minClusterSize
							&& currentCluster.value >= ObjectFinder.rootLowerBound + ObjectFinder.clusterDeviation
							&& currentCluster.value <= ObjectFinder.rootUpperBound - ObjectFinder.clusterDeviation) 
					{
						//only add a cluster to the data structure if it is large enough.
						largeClusters++;
						ArrayList<Cluster> clusterList = clusterValue_clusters_MAP.get(currentCluster.value);
						
						try {
							clusterList.add(currentCluster);
						} catch (Exception e) {
							System.out.println("Value at failure: ");
							System.out.println(currentCluster.value);
						}
						clusterValue_clusters_MAP.put(currentCluster.value, clusterList);
					}
					
					for (Point p: currentCluster.points) {
						// to allow these points to be added to other clusters too.
						p.accountedForInSameCluster = false;
					}
				}
				
				nextPoint = newClusterToBeProcessed.get(0);
				if (nextPoint.hasCluster) {
					newClusterToBeProcessed.remove(0);
					continue;
				}
				fromSameCluster = false;
				currentCluster = new Cluster(nextPoint, sliceNumber);
				
			}

			if (count %10000 == 0) {
				//System.out.println("count: " + count + ", " + nextPoint + ", val: " + nextPoint.value + ", hasCl: " + nextPoint.hasCluster + ", accNew: " + nextPoint.accountedForInNewCluster + ", accSame: " + nextPoint.accountedForInSameCluster + ", considered: " + nextPoint.considered + ", addedNew: " + nextPoint.addedToNewClusterList + ", addedSame: " + nextPoint.addedToSameClusterList + ", fromSame: " + fromSameCluster + ", sameSize: " + sameClusterToBeProcessed.size() + ", newSize: " + newClusterToBeProcessed.size());
			}
			
			if (nextPoint.x - 1 >= xMin) {
				considerPoint(nextPoint.x - 1, nextPoint.y, currentCluster, nextPoint);
			}
			
			if (nextPoint.x + 1 <= xMax) {
				considerPoint(nextPoint.x + 1, nextPoint.y, currentCluster, nextPoint);
			}
			
			if (nextPoint.y - 1 >= yMin) {
				considerPoint(nextPoint.x, nextPoint.y - 1, currentCluster, nextPoint);
			}
			
			if (nextPoint.y + 1 <= yMax) {
				considerPoint(nextPoint.x, nextPoint.y + 1, currentCluster, nextPoint);
			}
			
			if (fromSameCluster) {
				sameClusterToBeProcessed.remove(0);
			}
			else {
				newClusterToBeProcessed.remove(0);
			}
			
		}

		System.out.println("finished while loop on start: " + this.start);
		
		for (int i = ObjectFinder.rootLowerBound + ObjectFinder.clusterDeviation;
					i <= ObjectFinder.rootUpperBound - ObjectFinder.clusterDeviation; i++) 
		{
			ArrayList<Cluster> clusters = clusterValue_clusters_MAP.get(i);
			for (Cluster c: clusters) {
				c.postProcessing();
			}
		}
		sliceNumber_clusterValue_clusters_MAP.put(sliceNumber, clusterValue_clusters_MAP);
		/*for (int i = ObjectFinder.rootLowerBound + ObjectFinder.clusterDeviation;
					i <= ObjectFinder.rootUpperBound - ObjectFinder.clusterDeviation; i++) 
		{
			System.out.println(this.start + " - " + this.end + ": value " + i + ", :");

			System.out.println(sliceNumber + ": " + i + ", " + clusterValue_clusters_MAP.get(i).size());
			
		}*/
		//System.out.println("LargeClusters: " + largeClusters);
	}
	
	public void considerPoint(int x, int y, Cluster currentCluster, Point prevPoint) {
		Point newPoint = points[x][y];
		
		//System.out.println("count: " + count + ", " + newPoint + ", prevPoint: " + prevPoint +", val: " + newPoint.value + ", hasCl: " + newPoint.hasCluster + ", accNew: " + newPoint.accountedForInNewCluster + ", accSame: " + newPoint.accountedForInSameCluster + ", considered: " + newPoint.considered + ", addedNew: " + newPoint.addedToNewClusterList + ", addedSame: " + newPoint.addedToSameClusterList + ", sameSize: " + sameClusterToBeProcessed.size() + ", newSize: " + newClusterToBeProcessed.size());
		
		boolean hasCluster = false;
		if (newPoint.hasCluster) {
			//then that cluster is either the currentCluster, [in which case nothing to do here]
			// or it is a completed cluster. Points in a completed cluster may be added to other clusters too.
			// but points in a completed cluster don't need to be processed again.
			if (newPoint.cluster == currentCluster) {
				return;
			}
			else {
				hasCluster = true;
			}
		}
		if (Math.abs(newPoint.value - currentCluster.value) <= ObjectFinder.clusterDeviation) {
			boolean sameValue = (newPoint.value == currentCluster.value);
			currentCluster.addPoint(newPoint, sameValue);
			//will be processed to complete this cluster
			
			addToSameClusterList(newPoint);
			
			if (!sameValue && !hasCluster) {
				//if the point isn't the same value, it needs to be processed as it's own cluster also.
				addToNewClusterList(newPoint);
			}
		}
		else {
			if (!hasCluster) {
				addToNewClusterList(newPoint);
			}
		}
		/*else {
			if (!hasCluster)
			currentCluster.addNeighbour(newPoint);
		}*/
			
	}
	public void addToSameClusterList(Point p) {
		//if it has already been added to the list -- then 
		
		if (!p.accountedForInSameCluster) {
			sameClusterToBeProcessed.add(p);
			p.accountedForInSameCluster = true;
			//System.out.println(p + " added to sameClusterList");

		}
	}
	
	public void addToNewClusterList(Point p) {
		if (!p.accountedForInNewCluster) {
			newClusterToBeProcessed.add(p);
			p.accountedForInNewCluster = true;
			//System.out.println(p + " added to newClusterList");
		}
	}
	
	//not implemented at this stage (while still divided into multiple threads) yet.
	public void findChains() {
		
		System.err.println("findChains is not implemented yet!!");
	}
		
}
/*class ObjectFinder implements Runnable {
	
	
	//TODO
	//problems:
	//- newPoint.hasCluster is no longer sufficient to rule out processing a point.
	// 	because points can now be used in multiple clusters, and processing a point is necessary to access its neighbours.
	//- need to be able to add points to sameClusterToBeProcessed without (always) changing that point's cluster. 
	//	[fixed!] -- made a change to cluster.addPoint, still TODO: apply it.
	//- need to store clusters by their central value so that I can discriminate in comparing a 4-5-6 with  only 5-6-7, not any 7-8-9.
	//- Could implement some level of chain-linkingAnalysis to the ObjectFinder (still in parallelised) --
	//	then use those results later on to combine across the arbitrary divisions [would speed it up].
	//- What if I want to change the limits for a root (1-2-3 and up allowed or.. etc. (?), or make it 8, or 32 bins rather than 16..
	//	Should make the actual numbers (4-5-6 / 11-12-13) at the boundaries changeable.
	//- While doing this, might as well fix the A-B-C-D-E-F-G... & B-C-D-E-F_G... problem.
	
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
		
		
		//start at 4-5-6 up to 11-12-13
		//compare the 5-6-7 slice 0 clustering with the clusters for:
		//4-5-6, 5-6-7 and 6-7-8 on slice 1 -- but not 7-8-9 or 3-4-5 (maximum jump of 1 & also 3-4-5 is too low I think)
		HashMap<Integer, ArrayList<Cluster>> centralValue_to_clusters = new HashMap<Integer, ArrayList<Cluster>>();
		ArrayList<Cluster> clusters;
		int minRootValue = 4;
		int maxRootValue = 13;
		for (int i = 0; i < maxStart - minStart; i++) {
		
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

			int currentClusterValue;
			
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
					currentClusterValue = currentPoint.value;
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
		if (newPoint.value == previous.value || newPoint.value == previous.value - 1 || newPoint.value == previous.value+1) {
			previous.cluster.addPoint(newPoint);
			if (newPoint.value == previous.value) {
				sameClusterToBeProcessed.add(newPoint);
			}
			else {
				
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
}*/

class Point {
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



class Cluster {
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
		String toReturn = "Cluster starting at " + points.get(0) + ","+this.z + " - ";
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

	public void calculateValues() {
		this.calculatedValues = true;
		this.area = getArea();
		this.aspectRatio = getAspectRatio();
		this.center = getCenter();
	}
	
	public int getSliceNumber() {
		return this.z;
	}
}
