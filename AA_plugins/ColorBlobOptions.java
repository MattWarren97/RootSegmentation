package AA_plugins;


public class ColorBlobOptions {
	public int rootLowerBound;
	public int rootUpperBound;
	public boolean printMatches;
	public int minClusterSize;
	public float maxCenterDistance;
	public float areaDifferenceWeight;
	public float aspectRatioDifferenceWeight;
	public float colourDifferenceWeight;
	
	public int minClusterChainLength;
	public float majorMinorRatioLimit;
	public float chainJoiningScaler;
	
	public int minSliceNumber, maxSliceNumber;
	public int minValue, maxValue;
	public int minArea, maxArea;
	public int minCenterX, maxCenterX, minCenterY, maxCenterY;
	
	
	
	//TODO: sanitise inputs.. finish both versions of constructor, 
	public ColorBlobOptions(int rootLowerBound, int rootUpperBound, boolean printMatches, int minClusterSize,
					float maxCenterDistance, float areaDifferenceWeight, float aspectRatioDifferenceWeight, 
					float colourDifferenceWeight, int minClusterChainLength, float majorMinorRatioLimit,
					float chainJoiningScaler) {
		
		this.rootLowerBound = rootLowerBound;
		this.rootUpperBound = rootUpperBound;
		this.printMatches = printMatches;
		this.minClusterSize = minClusterSize;
		this.maxCenterDistance = maxCenterDistance;
		this.areaDifferenceWeight = areaDifferenceWeight;
		this.aspectRatioDifferenceWeight = aspectRatioDifferenceWeight;
		this.colourDifferenceWeight = colourDifferenceWeight;
		this.minClusterChainLength = minClusterChainLength;
		this.majorMinorRatioLimit = majorMinorRatioLimit;
		this.chainJoiningScaler = chainJoiningScaler;

	}


	public ColorBlobOptions(int rootLowerBound, int rootUpperBound, boolean printMatches, int minClusterSize,
					float maxCenterDistance, float areaDifferenceWeight, float aspectRatioDifferenceWeight,
					float colourDifferenceWeight, int minClusterChainLength, float majorMinorRatioLimit,
					float chainJoiningScaler, int minSliceNumber, int maxSliceNumber, int minValue, 
					int maxValue, int minArea, int maxArea, int minCenterX, int maxCenterX, int minCenterY,
					int maxCenterY,	boolean printDifferences) {


		this.rootLowerBound = rootLowerBound;
		this.rootUpperBound = rootUpperBound;
		this.printMatches = printMatches;
		this.minClusterSize = minClusterSize;
		this.maxCenterDistance = maxCenterDistance;
		this.areaDifferenceWeight = areaDifferenceWeight;
		this.aspectRatioDifferenceWeight = aspectRatioDifferenceWeight;
		this.colourDifferenceWeight = colourDifferenceWeight;
		this.minClusterChainLength = minClusterChainLength;
		this.majorMinorRatioLimit = majorMinorRatioLimit;
		this.chainJoiningScaler = chainJoiningScaler;

		this.minSliceNumber = minSliceNumber;
		this.maxSliceNumber = maxSliceNumber;
		this.minValue = minValue;
		this.maxValue = maxValue;
		this.minArea = minArea;
		this.maxArea = maxArea;
		this.minCenterX = minCenterX;
		this.maxCenterX = maxCenterX;
		this.minCenterY = minCenterY;
		this.maxCenterY = maxCenterY;

	}
}