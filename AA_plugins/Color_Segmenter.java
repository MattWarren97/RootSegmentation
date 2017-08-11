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
		//displayClusterPairs();
		findChainedClusters();
	}
	
	public void findConnectedClusters() {
		DualHashBidiMap<Cluster, Cluster> connectedClusters;
		System.out.println("started findConnectedClusters");
		for (int sliceNumber = 1; sliceNumber <= this.image.getStackSize() - 1; sliceNumber++) {
			//System.out.println("Started connecting clusters on " + sliceNumber);
			connectedClusters = new DualHashBidiMap<Cluster, Cluster>();
			
			for (int clusterValue = ObjectFinder.rootLowerBound + ObjectFinder.clusterDeviation;
						clusterValue < ObjectFinder.rootUpperBound - ObjectFinder.clusterDeviation; 
								clusterValue++) 
			{
				HashMap<Integer, ArrayList<Cluster>> current_clusterValue_clusters_MAP = sliceNumber_clusterValue_clusters_MAP.get(sliceNumber);
				HashMap<Integer, ArrayList<Cluster>> next_clusterValue_clusters_MAP = sliceNumber_clusterValue_clusters_MAP.get(sliceNumber+1);
				ArrayList<Cluster> clusters1 = current_clusterValue_clusters_MAP.get(clusterValue);
				
				//System.out.println("Clusters1 size: " + clusters1.size());
				for (Cluster c1: clusters1) {
					boolean isInteresting = false;
					if (sliceNumber >= 33 && sliceNumber < 53) {
						if (c1.center[0] >= 210 && c1.center[0] < 240) {
							if (c1.center[1] >= 265 && c1.center[1] < 295) {
								if (c1.value >= 16 && c1.value < 24) {
									isInteresting = true;
									System.out.println("Potential cluster - " + c1);
								}
							}
						}
					}
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
							//System.out.println("No clusters at value " + nextClusterValue + " - continuing");
							continue;
						}
						
						ArrayList<Cluster> clusters2 = next_clusterValue_clusters_MAP.get(nextClusterValue);
						if (clusters2.isEmpty()) {
							System.out.println(sliceNumber + ", " + nextClusterValue + " has empty clusters2");
						}
						//System.out.println("clusters2 List length is " + clusters2.size());
						//Set clusters2Set = new HashSet(clusters2);
						//System.out.println("clusters2 set length is " + clusters2Set.size());
					
						for (Cluster c2 : clusters2) {
							Float difference = compareClusters(c1, c2);
							if (difference == null) {
								//clusters are too far apart to attempt joining...
								continue;
							}
							if (difference < minDifference || minDifference == -1) {
								//either no cluster has yet set a difference 'mark', or some other cluster has and this c2 bettered it.
								minDifference = difference;
								bestCluster = c2;
							}
						}
					}
					
					if (minDifference != -1) {
						//There is some cluster c2 that fits the criteria for c1 to match with c2.
						
						if (connectedClusters.containsValue(bestCluster)) {
							//System.out.println("Ok - there is a duplicate mapping to bestCluster!");
							//then some other cluster also mapped to best cluster. This can't be allowed.
							//System.out.println("bestCluster is " + bestCluster);
							//System.out.println("c1 is " + c1);
							Cluster otherC1 = connectedClusters.getKey(bestCluster);
							//System.out.println("otherc1 is " + otherC1);
							Float c1DiffBest = minDifference;
							Float otherC1DiffBest = compareClusters(otherC1, bestCluster);
							//System.out.println("c1; otherc1 - " + c1DiffBest + "; " + otherC1DiffBest);
							if (c1DiffBest < otherC1DiffBest) {
								//System.out.println("c1 better!");
								//c1 is the best match for bestCluster --
								//remove the old association, add the new association, and prepare the 'back-propagation' of this change.
								connectedClusters.remove(otherC1);
								connectedClusters.put(c1, bestCluster);
								if (isInteresting) {
									System.out.println("Matched with " + bestCluster);

								}
								prepareToBackPropagate(c1, otherC1);
							}
							else {
								//System.out.println("otherC1Best");
								//otherC1 is the best match for bestCluster.
								prepareToBackPropagate(otherC1, c1);
							}

						}
						else {
							//simple case
							if (isInteresting) {
								System.out.println("Matched with " + bestCluster);
							}
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
		//System.out.println("Starting backPropagating on " + startSlice + " size of replacements: " + this.replacements.size());
		//if there si nothing to backPropagate, then does nothing.
		if (this.replacements == null) {
			//System.out.println("replacements was null on slice " + startSlice);
			return;
		}
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
		//System.out.println("Got through those initial checks");
		DualHashBidiMap<Cluster, Cluster> connectedClusters = pairedClustersBySlice.get(startSlice);
		DualHashBidiMap<Cluster, Cluster> prevConnectedClusters = pairedClustersBySlice.get(startSlice - 1);
		
		HashMap<Cluster, Cluster> replacements = new HashMap<Cluster, Cluster>(this.replacements);
		this.replacements = null;
		Iterator<Cluster> it = replacements.keySet().iterator();
		//System.out.println("About to begin while loop");
		while(it.hasNext()) {
			Cluster replaced = it.next();
			//System.out.println("Replaced: " + replaced);
			
			Cluster replacement = replacements.get(replaced);
			
			//System.out.println("Replacement: " + replacement);
			
			//System.out.println(replaced == replacement);
			
			//what if A replaces C, but D then replaces A? 
			//System.out.println("replacement: " + replacement);
			while (replacements.containsKey(replacement)) {
				replacement = replacements.get(replacement);
				//System.out.println("replacement: " + replacement);
				//System.out.println(replacements);
				//try {
				//	Thread.sleep(1000);
				//} catch (Exception e) {}
			}
			//System.out.println("Outside second while loop");
			//TODO fix the iterator (to remove items).
			
			
			//if 'replacement' replaces 'replaced', fancy back-propagation is only needed when:
			//('preExistingReplacedKey'->'replaced') exists in the prevConnectedClusters.
			if (prevConnectedClusters.containsValue(replaced)) {
				
				//System.out.println("A - true");
				Cluster preExistingReplacedKey = prevConnectedClusters.getKey(replaced);
				
				//I want to set (preExistingReplacedKey -> replacement) as a pair [but can only do this if they meet certain conditions].
				if(canCompareClusters(preExistingReplacedKey, replacement)) {
					//System.out.println("B - true");
					//It's possible that replacement already had a different key [another conflict would arise].
					if (prevConnectedClusters.containsValue(replacement)) {
						//System.out.println("C - true");
						Cluster preExistingReplacementKey = prevConnectedClusters.getKey(replacement);
						//then we will need to backPropagate further.
						//Only one of [(preExistingReplacedKey -> replacement) & (preExistingReplacementKey -> replacement)] can be used.
						Float replacedKeyDiff = compareClusters(preExistingReplacedKey, replacement);
						Float replacementKeyDiff = compareClusters(preExistingReplacementKey, replacement);
						
						if (replacedKeyDiff < replacementKeyDiff) {
							//System.out.println("D - true");
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
							//System.out.println("D - false");
							//replacementKeyDiff is still the best match for replacement.
							//so no immediate changes to make here.
							//need to backPropagate in case some (F->preExistingReplacedKey) exists that might need to be updated to:
							//(F->preExistingReplacementKey)  [[in order to keep that chain going]].
							prepareToBackPropagate(preExistingReplacementKey, preExistingReplacedKey);
						}
					}
					else {
						//System.out.println("C - false");
						//if not, then we can end this chain of changes here.
						//replace (preExistingReplacedKey -> replaced) with (preExistingReplacedKey -> replacement)
						prevConnectedClusters.put(preExistingReplacedKey, replacement);
					}
					
				}
				else {
					//System.out.println("B - false");
					//if preExistingReplacedKey can't link to replacement, then nothing need be changed/
					//(preExistingReplacedKey -> replaced) will remain in prevConnectedClusters.
				}
			}
			
			else {
				//System.out.println("A - false");
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
		
		/*System.out.println("begin findChainedClusters()");
		for (int sliceNumber = 1; sliceNumber <= this.image.getStackSize() - 1; sliceNumber++) {
			DualHashBidiMap<Cluster, Cluster> connectedClusters = pairedClustersBySlice.get(sliceNumber);
			ArrayList<Cluster> valuesList = new ArrayList<Cluster>(connectedClusters.values());
			Set<Cluster> valuesSet = new HashSet<Cluster>(connectedClusters.values());
			System.out.println("valuesList " + valuesList.size());
			System.out.println("valuesSet " + valuesSet.size());
			if (valuesList.size() != valuesSet.size()) {
				System.out.println("On slice " + sliceNumber + ", valuesList: " + valuesList.size() + ", valuesSet: " + valuesSet.size());
			}
		}
		System.out.println("finished findChainedClusters");
		*/
		
		HashMap<Integer, Integer> clusterLengths = new HashMap<Integer, Integer>();
		int stackSize = this.image.getStackSize();
		for (sliceNumber = 1; sliceNumber <= stackSize - Color_Segmenter.minClusterChainLength; sliceNumber++) {
			DualHashBidiMap<Cluster, Cluster> connectedClusters = pairedClustersBySlice.get(sliceNumber);
			
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
				if (clusterLengths.containsKey(length)) {
					clusterLengths.put(length, clusterLengths.get(length) + 1);
				}
				else {
					clusterLengths.put(length, 1);
				}
				if (length > Color_Segmenter.minClusterChainLength) {
					//System.out.println("Chain from sliceNumber: " + sliceNumber + " has a length of at least " + length);
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
		
		System.out.println("Cluster lengths: " + clusterLengths);
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

		float comparison = (-colourDifference*colourDifferenceWeight) + aspectRatioChange*aspectRatioDifferenceWeight + areaDifference*areaDifferenceWeight;
		return comparison;
	}

}

class ObjectFinder implements Runnable {
	
	
	//TODO: Do I use neighbours for anything? I don't think I do -- could that be removed?
	
	int start, end;
	ImageStack stack;
	Color_Segmenter cs;
	
	static int rootLowerBound = 8;
	static int rootUpperBound = 26;
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
		
		
		
		
	public void findClusters(ImageProcessor ip, int clusterValue) {
		
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
				if (points[i][j].value == clusterValue) {
					pointsAtValue.add(points[i][j]);
				}
			}
		}
				
		while(pointsAtValue.size() != 0) {
			Point nextPoint = pointsAtValue.remove(0);
			Cluster currentCluster = new Cluster(nextPoint, sliceNumber);
			sameClusterToBeProcessed = new ArrayList<Point>();
			sameClusterToBeProcessed.add(nextPoint);
			processed = new HashSet<Point>();
			processed.add(nextPoint);
			
			while(!sameClusterToBeProcessed.isEmpty()) {
				nextPoint = sameClusterToBeProcessed.remove(0); //the value at index 0 is returned while being removed.
				if (nextPoint.x - 1 >= xMin) {
					considerPoint(nextPoint.x - 1, nextPoint.y, currentCluster);
				}
				
				if (nextPoint.x + 1 <= xMax) {
					considerPoint(nextPoint.x + 1, nextPoint.y, currentCluster);
				}
				
				if (nextPoint.y - 1 >= yMin) {
					considerPoint(nextPoint.x, nextPoint.y - 1, currentCluster);
				}
				
				if (nextPoint.y + 1 <= yMax) {
					considerPoint(nextPoint.x, nextPoint.y + 1, currentCluster);
				}
				
			}
			//System.out.println("sameClusterToBeProcessed is empty!");
			clustersAtValue.add(currentCluster);
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
			
			ImagePlus imp = new ImagePlus("Display Processed Clusters", highlightedClusters);
			
			IJ.run(imp, "Median...", "2");
			IJ.run(imp, "Invert", "");
			IJ.run(imp, "Dilate", "");
			IJ.run(imp, "Fill Holes", "");
			IJ.run(imp, "Erode", "");
			IJ.run(imp, "Watershed", "");
			
			
			//TODO: Find the code implementation of watershed -- see if it is possible to convert the resulting data structures into
			//the new cluster list. Do I need to do that?? This seems not too inefficient for now...
			
			findClusters(highlightedClusters, 0);
			
			ArrayList<Cluster> largeClusters = new ArrayList<Cluster>();
			for (Cluster c: clustersAtValue) {
				if (c.getArea() >= Color_Segmenter.minClusterSize) {
					largeClusters.add(c);
					c.postProcessing();
				}
			}
			clusterValue_clusters_MAP.put(clusterValue, largeClusters);
		}
			
		
	}
	
	public void considerPoint(int x, int y, Cluster c) {
		Point newPoint = points[x][y];
		if (processed.contains(newPoint)) {
			return;
		}
		int valueDifference = Math.abs(newPoint.value - c.value);
		if (valueDifference <= ObjectFinder.clusterDeviation) {
			sameClusterToBeProcessed.add(newPoint);
			if (valueDifference == 0) {
				pointsAtValue.remove(newPoint);
				c.addPoint(newPoint, true);
			}
			else {
				c.addPoint(newPoint, false);
			}
		}
		processed.add(newPoint);
	}
	
	//not implemented at this stage (while still divided into multiple threads) yet.
	public void findChains() {
		
		//System.err.println("findChains is not implemented yet!!");
		System.err.println("Finished finding clusters from slices: " + start + "-" + end);
	}
		
}
