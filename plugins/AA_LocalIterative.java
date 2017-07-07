import ij.*; //   \\Cseg_2\erc\ADMIN\Programmes\Fiji_201610_JAVA8.app\jars\ij-1.51g.jar needs to be added to classpath when compiling(-cp))
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import java.awt.*;
import java.util.Arrays;
import ij.macro.Interpreter;
import ij.plugin.Selection;
import ij.plugin.ImageCalculator;
import ij.plugin.filter.Analyzer;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.gui.ImageWindow;
import ij.plugin.filter.ThresholdToSelection;
import ij.gui.Roi;
import ij.plugin.filter.EDM;
import ij.gui.OvalRoi;
import ij.measure.Calibration;

public class AA_LocalIterative implements PlugInFilter {
	
	static final int DIMENSIONS_INCREMENT = 10; //must be multiple of 2.
	//each time run is called, new focus area is selected to have DIMENSIONS_INCREMENT longer width and height
	//(involves moving the start back half this difference too).
	
	static final int ENLARGE_FACTOR = 10;
	static final int MED_RD = 5;
	static final int X = 500;
	static final int Y = 500;
	static final int Z = 300;
	int xStart;
	int yStart;
	int zStart;
	//static final int RM_OUT_FILTER = 4;
	//static final int[] SD_ARRAY = {4, 5, 6};
	//static final int[] ERODE_ARRAY = {19,7,3};
	//static final int[] MAX_STD = {8, 9, 10};
	int callCount;
	double gauss_mean;
	double gauss_std;
	ImageProcessor ipCopy;
	ImageProcessor fullSize;
	
	//idea is to use ip.clone() -- creates an ip that shares the same pixel array, then use the getPixelsCopy() method and set the new ipcopy to that.
	//then try to mask on that, so I can implement the adjustGaussMeanStd method...
	Roi focusArea;
	
	
	//these variables are from Object_Counter3D - but are used in multiple methods.
	int[] tag;
	int Width;
	int Height;
	int NbSlices;
	
	
	public AA_LocalIterative() {
		System.err.println("Initialising log");
		callCount = 0;
		//gauss_mean = 104;
		gauss_std = 6;
		//focusArea = new Roi(new Rectangle(0, 0, 31, 12));
		//xStart = 329;
		//yStart = 181;
		//zStart = 169;
		gauss_mean = 85;
		focusArea = new Roi(new Rectangle(0, 0, 22, 22));
		xStart = 218;
		yStart = 298;
		zStart = 92;
	}
/*
	public void run(ImageProcessor ip) {
		ImagePlus iPlus = new ImagePlus("ip", ip);
		Roi selectionRoi = selectCentralObject(iPlus);
		iPlus.show();
	}*/
	public void run(ImageProcessor ip) {
		
		if (callCount == 0) {
			//get the user to select an inital location (and slice) of a root leaving the stem.
			//set zTarget to the Z location
			//set focusArea to the initialArea they selected
			
		}
		
		if (callCount < zStart-1) {
			System.out.println("Looking at slice: " + (callCount+1) + ", target starts on slice: " + zStart);
			callCount++;
			return;
		}
		
		callCount++;

		//make adjustments to ROI to allow for changing location of root.
		focusArea.setLocation(xStart, yStart);
		try {
			focusArea = enlargeRoi(focusArea);
		} catch (Exception e) {
			System.err.println("Selection on image " + callCount + " was too small to enlarge -- exception thrown");
		}
		//System.out.println("X: " + focusArea.getXBase() + ", Y: " + focusArea.getYBase());
		//Rectangle r = focusArea.getBounds();
		
		
		//It's possible that x and y / x+width or y+height could go out of the image bounds -- TODO check for this.
		//r.setBounds((int) r.getX()- DIMENSIONS_INCREMENT/2+xStart, (int) r.getY()- DIMENSIONS_INCREMENT/2+yStart, (int) r.getWidth()+ DIMENSIONS_INCREMENT, (int) r.getHeight()+ DIMENSIONS_INCREMENT);
		//focusArea = new Roi(r);
		
		//System.out.println("focusArea updated- X: " + ((int) r.getX()) + ", Y: " + ((int) r.getY()) + ", Width: " + ((int) r.getWidth()) + ", Height: " + ((int) r.getHeight()));
		System.out.println("focusArea updated");
		
		fullSize = (ImageProcessor) ip.clone();
		fullSize.setPixels(ip.getPixelsCopy());
		ip.setRoi(focusArea);
		
		ip = ip.crop();
				
		ipCopy=(ImageProcessor) ip.clone();
		ipCopy.setPixels(ip.getPixelsCopy());
		ImagePlus iPlusCopy = new ImagePlus("ipcopy" + callCount, ipCopy);

		runGaussianMask(ip);
		medianFilter(ip, MED_RD);
		applyThreshold(ip, 75, 255);
		//medianFilter(ip, MED_RD);
		
		ImagePlus iPlus = new ImagePlus("ip" + callCount, ip);
		//iPlus.show();
		
		//Roi selectionRoi = selectFromMask(iPlus);
		Roi selectionRoi = selectCentralObject(iPlus);
		applyRoi(iPlusCopy, selectionRoi, (int) selectionRoi.getXBase(), (int) selectionRoi.getYBase());
		
		int measurements = Measurements.MEAN + Measurements.STD_DEV;
		
		ResultsTable rt = new ResultsTable();
		Analyzer an = new Analyzer(iPlusCopy, measurements, rt);
		
		an.measure();
		double newMean = rt.getValue("Mean", rt.getCounter()-1);
		double newStd = rt.getValue("StdDev", rt.getCounter()-1);
		
		gauss_mean = newMean;

		System.out.println("Image: " + callCount + ", Mean: " + gauss_mean + ", Std_dev: " + gauss_std);
		
		focusArea = selectionRoi;
		System.out.println("new focus area was- X: " + ((int) focusArea.getXBase()) + ", Y: " + ((int) focusArea.getYBase()));
		xStart -= (ENLARGE_FACTOR-focusArea.getXBase());
		yStart -= (ENLARGE_FACTOR-focusArea.getYBase());
		System.out.println("new xStart, yStart is " + xStart + ", " + yStart);
		iPlusCopy.deleteRoi();
		iPlus.deleteRoi();
		
		try {
			//Thread.sleep(500);
		} catch(Exception e) {}
		
	}
		
	//method copied across from https://imagej.nih.gov/ij/developer/source/ij/plugin/RoiEnlarger.java.html
	private Roi enlargeRoi(Roi roi) {
		roi = (Roi) roi.clone();
		int type = roi.getType();
        int n = ENLARGE_FACTOR;
        if (type==Roi.RECTANGLE || type==Roi.OVAL)
            return enlargeRectOrOval(roi, n);
        Rectangle bounds = roi.getBounds();
        int width = bounds.width;
        int height = bounds.height;
        width += 2*n +2;
        height += 2*n +2;
        ImageProcessor ip = new ByteProcessor(width, height);
        ip.invert();
        roi.setLocation(n+1, n+1);
        ip.setColor(0);
        ip.fill(roi);
        roi.setLocation(bounds.x, bounds.y);
        boolean bb = Prefs.blackBackground;
        Prefs.blackBackground = true;
        new EDM().toEDM(ip);
        //new ImagePlus("ip", ip).show();
        Prefs.blackBackground = bb;
        ip.setThreshold(0, n, ImageProcessor.NO_LUT_UPDATE);
        Roi roi2 = (new ThresholdToSelection()).convert(ip);
        if (roi2==null)
            return roi;
        roi2.setLocation(bounds.x-n, bounds.y-n);
        roi2.setStrokeColor(roi.getStrokeColor());
        if (roi.getStroke()!=null)
            roi2.setStroke(roi.getStroke());
        return roi2;
	}
	
	    
    private Roi enlargeRectOrOval(Roi roi, int n) {
        Rectangle bounds = roi.getBounds();
        bounds.x -= n;
        bounds.y -= n;
        bounds.width += 2*n;
        bounds.height += 2*n;
        if (bounds.width<=0 || bounds.height<=0)
            return roi;
        if (roi.getType()==Roi.RECTANGLE)
            return new Roi(bounds.x, bounds.y, bounds.width, bounds.height);
        else
            return new OvalRoi(bounds.x, bounds.y, bounds.width, bounds.height);
    }
		
	//method derived from https://imagej.nih.gov/ij/source/ij/plugin/Selection.java
	//All I've done is rewritten that method to be more convenient [doesn't need an ImageWindow object]
		private Roi selectFromMask(ImagePlus iPlus) {
		//iPlus.show();
		ImageProcessor ip = iPlus.getProcessor();
		
		int selectedCount = 0;
		byte[] pixels = (byte[]) ip.getPixels();
		for (int i = 0; i < pixels.length; i++) {
			if (pixels[i] == -1) {
				selectedCount++;
			}
		}
		System.out.println("selectFromMask, selected count is " + selectedCount);
		
		
		//We are using Fiji 8 with ImageJ 2.0.0.
		//I can't find sources for ImageJ 2.0.0, only 1.5.
		//In 1.5 the 'isBinary()' method will always return false.
		//In 2.0.0 it will seemingly return true sometimes, but not always, and I don't know what the conditions are.
		//actually, it appears the images were not binary...
		
		
		if (!ip.isBinary()) {
			IJ.error("SelectionFromMask", "Image not recognised as binary image, selection from mask impossible, on image: " + callCount);
			return null;
		}
		

		int threshold = ip.isInvertedLut()?0:255;
		//only values equal to white will be selected.
		ip.setThreshold(threshold, threshold, ImageProcessor.NO_LUT_UPDATE);
		
		
		//IJ.runPlugIn("ij.plugin.filter.ThresholdToSelection", "");
		//Trying to simulate the exact same thing below, but:
		//my method here doesn't include the line:
		//'tts.setup(" ", WindowManager.getCurrentImage()) [paraphrased].
		//I want to be able to just pass in the ImagePlus to use.
		ThresholdToSelection tts = new ThresholdToSelection();
		tts.setup("", iPlus);
		
		
		prepareProcessor(ip, iPlus);
		
		tts.run(ip);

		Roi selectionRoi = iPlus.getRoi();
		Rectangle r = selectionRoi.getBounds();
		System.out.println("new selection has bounds " + r.getX() + ", " + r.getY() + ", " + r.getWidth() + ", " + r.getHeight());
		return selectionRoi;
	}
	
	//I don't know how much of this method is actually required for ThresholdToSelection, but I'll use it all for now.
	//from https://github.com/imagej/imagej1/blob/master/ij/plugin/filter/PlugInFilterRunner.java
	private void prepareProcessor(ImageProcessor ip, ImagePlus imp) {
		ImageProcessor mask = imp.getMask();
		Roi roi = imp.getRoi();
		if (roi!=null && roi.isArea())
			ip.setRoi(roi);
		else
			ip.setRoi((Roi)null);
		if (imp.getStackSize()>1) {
			ImageProcessor ip2 = imp.getProcessor();
			double min1 = ip2.getMinThreshold();
			double max1 = ip2.getMaxThreshold();
			double min2 = ip.getMinThreshold();
			double max2 = ip.getMaxThreshold();
			if (min1!=ImageProcessor.NO_THRESHOLD && (min1!=min2||max1!=max2))
				ip.setThreshold(min1, max1, ImageProcessor.NO_LUT_UPDATE);
		}
		//float[] cTable = imp.getCalibration().getCTable();
		//ip.setCalibrationTable(cTable);
}
	
	private void applyRoi(ImagePlus iPlus, Roi roi, int xOffset, int yOffset) {
		roi.setLocation(xOffset, yOffset);
		iPlus.setRoi(roi);
	}
			
	
	private void adjustGaussMeanStd(ImageProcessor ip) {
		
		//ip.applyTable(pixelsTable);
	}
	
	private void runGaussianMask(ImageProcessor ip) {
		//It would be quicker to find the gaussianValue for each pixel, then decide whether it meets the threshold criteria and create the mask pixel by pixel.
		//but I'm not quite sure how to do that, so will do it here in two steps (threshold, mask) that both need to create their own image.
		

		//System.err.println("Call count is aaa " + callCount);
		byte[] pixels = (byte[]) ip.getPixels();
		
		int width = ip.getWidth();
		int height = ip.getHeight();
		Rectangle r = new Rectangle(0,0,width,height);
		System.err.println("Width: " + r.width + ", Height: " + r.height + ", y: " + r.y + ", x: " + r.x);

		int offset, i;
		for (int y = r.y; y < (r.y+r.height); y++) {
			offset = y*width;
			for (int x = r.x; x < (r.x+r.width); x++) {
				i = offset+x;
				double newValue = Math.floor((Math.exp(-((Math.pow(pixels[i]-gauss_mean, 2)/(2*Math.pow(gauss_std, 2))))))*255);
				int newInt = (int) newValue;
				byte newByte = (byte) newInt;
				pixels[i] = newByte;
			}
		}
		

		
	}
	
	private void applyThreshold(ImageProcessor ip, int lowLimit, int highLimit) {
		//copying some lines from the threshold code (https://imagej.nih.gov/ij/source/ij/plugin/Thresholder.java)
		int[] lut = new int[256];
		int whiteColor = 255;
		int blackColor = 0;
		for (int i = 0; i < 256; i++) {
			if (i>=lowLimit  && i <= highLimit) {
				lut[i] = whiteColor;
			}
			else {
				lut[i] = blackColor;
			}
		}
		ip.applyTable(lut);
		ip.setBinaryThreshold();
	}
	
		//this implementation from http://svg.dmi.unict.it/iplab/imagej/Plugins/Forensics/Median_filter2/Median_Filter.html
	private void medianFilter(ImageProcessor ip, int radius) {

		Rectangle r = ip.getRoi().getBounds();
		
		int width = (int) r.width;
		int height = (int) r.height;
		
		byte[] pixels = (byte[]) ip.getPixels();
		
		int[] tmp=new int[pixels.length];
		for (int i=0;i<pixels.length;i++)
			tmp[i]=pixels[i]&0xff; //this.... is just pixels[i], surely?
		
		int[][] arrays = create2DIntArray(pixels, width, height);
		
		int [][] medianArray = new int [width][height];
		for(int j=0;j<height;j++)
			for(int i=0;i<width;i++)
				medianArray[i][j] = pixelMedian(arrays,radius,width,height,i,j);
		
		int[] output = array_2d_to_1d(medianArray, width, height);
			
		for(int j=0;j<output.length;j++)
			pixels[j]=(byte)output[j];
	}
	
	private int pixelMedian(int[][] array2d, int radius, int width, int height, int x, int y) {
		
        int sum = 0;
        int countInRange = 0;
		int[] inRadius=new int[radius*radius];;
        for(int j=0;j<radius;j++)
        {
            for(int i=0;i<radius;i++)
            {
	            if(((x-1+i)>=0) && ((y-1+j)>=0) && ((x-1+i)<width) && ((y-1+j)<height))
                {
					inRadius[countInRange]=array2d[x-1+i][y-1+j];
	                countInRange++;
	            }
            }
        }
		Arrays.sort(inRadius);
        if(countInRange==0) 
            return 0;
		int medianIndex = (int)(countInRange/2);
        return (inRadius[medianIndex]);
    }
	/* removeOutliers is applied in the original macro
	//but it is applied to a binary image, so it is (I believe) no different to a median filter.
	private void removeOutliers(ImageProcessor ip, int radius, int threshold) {
	
		Rectangle r = ip.getRoi().getBounds();
		
		int width = (int) r.width;
		int height = (int) r.height;
		
		byte[] pixels = (byte[]) ip.getPixels();
		
		int[] tmp=new int[pixels.length];
		for (int i=0;i<pixels.length;i++)
			tmp[i]=pixels[i]&0xff; //this.... is just pixels[i], surely?
		
		int[][] arrays = create2DIntArray(pixels, width, height);
		
		int [][] medianArray = new int [width][height];
		for(int j=0;j<height;j++)
			for(int i=0;i<width;i++) {
				int centerValue = pixels[width*j+i];
				int medianvalue = pixelMedian(arrays, radius, width, height, i, j);
				if (centerValue - medianValue > 50) {
					medianArray[i][j] = centerValue;
				}
				else {
					medianArray[i][j] = 0;
					
			}
		
		int[] output = array_2d_to_1d(medianArray, width, height);
			
		for(int j=0;j<output.length;j++)
			pixels[j]=(byte)output[j];
	}
	*/
	
	private int[][] create2DIntArray(byte[] pix, int width, int height) {
		int[][] array2d = new int[width][height];
		
		for (int i = 0; i<width; i++) {
			for(int j = 0; j < height; j++) {
				array2d[i][j] = pix[i+(j*width)];
			}
		}
		return array2d;
	}
	
	private int[] array_2d_to_1d(int[][] values, int width, int height) {
		int[] output = new int [width*height];
		
		for(int i=0;i<width;i++) {
			for(int j=0;j<height;j++) {
				output[i+(j*width)] = values[i][j];
			}
		}
		
		return output;
	}
	
	//lines modified from https://imagej.nih.gov/ij/plugins/track/Object_Counter3D.java
	private Roi selectCentralObject(ImagePlus iPlus) {
		ImageProcessor ip = iPlus.getProcessor();
		int selectedCount = 0;
		byte[] pixs = (byte[]) ip.getPixels();
		for (int i = 0; i < pixs.length; i++) {
			if (pixs[i] == -1) {
				selectedCount++;
			}
		}
		System.out.println("selectCentralObject, selected count is " + selectedCount);
        int x, y, z;
        int xn, yn, zn;
        int i, j, k, arrayIndex, offset;
        int voisX = -1, voisY = -1, voisZ = -1;
		Width = iPlus.getWidth();
		Height = iPlus.getHeight();
		NbSlices = iPlus.getStackSize(); //TODO do I want this to be larger than 1 ever?? I think not.
        int maxX = Width-1, maxY=Height-1;

		int minSize = 10;
		int maxSize = Width*Height;
		
        int index;
        int val;
        double col;

        int minTag;
        int minTagOld;

		Calibration cal;
        cal = iPlus.getCalibration();
        if (cal == null ) {
            cal = new Calibration(iPlus);
        }
        double pixelDepth = cal.pixelDepth;
        double pixelWidth = cal.pixelWidth;
        double pixelHeight = cal.pixelHeight;
        double zOrigin = cal.zOrigin;
        double yOrigin = cal.yOrigin;
        double xOrigin = cal.xOrigin;
        double voxelSize = pixelDepth * pixelWidth * pixelHeight;

        int[] pict=new int [Height*Width*NbSlices];
        boolean[] thr=new boolean [Height*Width*NbSlices];
        tag=new int [Height*Width*NbSlices];
        boolean[] surf=new boolean [Height*Width*NbSlices];
        Arrays.fill(thr,false);
        Arrays.fill(surf,false);

        //Load the image in a one dimension array
		
		//ME: I'm pretty sure this could be done with ip.getPixels();
        ImageStack stack = iPlus.getStack();
        arrayIndex=0;
		int ThrVal = 0;
        for (z=1; z<=NbSlices; z++) {
            ip = stack.getProcessor(z);
            for (y=0; y<Height;y++) {
                for (x=0; x<Width;x++) {
                    int PixVal=ip.getPixel(x, y);
                    pict[arrayIndex]=PixVal;
					if (PixVal>ThrVal){
						thr[arrayIndex]=true;
						//TODO The current problem is that the black '0 space' is being counted.
						// I need to work uot why and stop it from being counted / stop it from being able to be the closest bit.
                    }
                    arrayIndex++;
                }
            }
        }

		//ME: IDs are used to work out what structures are connected...
		//I won't touch this code for now
		
        //First ID attribution
        int tagvois;
        int ID=1;
        arrayIndex=0;
        for (z=1; z<=NbSlices; z++){
            for (y=0; y<Height; y++){
                for (x=0; x<Width; x++){
                    if (thr[arrayIndex]){
                        tag[arrayIndex]=ID;
                        minTag=ID;
                        i=0;
                        //Find the minimum tag in the neighbours pixels
                        for (voisZ=z-1;voisZ<=z+1;voisZ++){
                            for (voisY=y-1;voisY<=y+1;voisY++){
                                for (voisX=x-1;voisX<=x+1;voisX++){
                                    if (withinBounds(voisX, voisY, voisZ)) {
                                        offset=offset(voisX, voisY, voisZ);
                                        if (thr[offset]){
                                            i++;
                                            tagvois = tag[offset];
                                            if (tagvois!=0 && tagvois<minTag) minTag=tagvois;
                                        }
                                    }
                                }
                            }
                        }
                        if (i!=27) surf[arrayIndex]=true;
                        tag[arrayIndex]=minTag;
                        if (minTag==ID){
                            ID++;
                        }
                    }
                    arrayIndex++;
                }
            }
        }
        ID++;

        //Minimization of IDs=connection of structures
        arrayIndex=0;
        for (z=1; z<=NbSlices; z++){
            for (y=0; y<Height; y++){
                for (x=0; x<Width; x++){
                    if (thr[arrayIndex]){
                        minTag=tag[arrayIndex];
                        //Find the minimum tag in the neighbours pixels
                        for (voisZ=z-1;voisZ<=z+1;voisZ++){
                            for (voisY=y-1;voisY<=y+1;voisY++){
                                for (voisX=x-1;voisX<=x+1;voisX++){
                                    if (withinBounds(voisX, voisY, voisZ)) {
                                        offset=offset(voisX, voisY, voisZ);
                                        if (thr[offset]){
                                            tagvois = tag[offset];
                                            if (tagvois!=0 && tagvois<minTag) minTag=tagvois;
                                        }
                                    }
                                }
                            }
                        }
                        //Replacing tag by the minimum tag found
                        for (voisZ=z-1;voisZ<=z+1;voisZ++){
                            for (voisY=y-1;voisY<=y+1;voisY++){
                                for (voisX=x-1;voisX<=x+1;voisX++){
                                    if (withinBounds(voisX, voisY, voisZ)) {
                                        offset=offset(voisX, voisY, voisZ);
                                        if (thr[offset]){
                                            tagvois = tag[offset];
                                            if (tagvois!=0 && tagvois!=minTag) replacetag(tagvois,minTag);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    arrayIndex++;
                }
            }
		}

        //Parameters determination 
		//0:volume; 1:surface; 2:intensity; 3:barycenter x; 4:barycenter y; 
		//5:barycenter z; 6:barycenter x int; 7:barycenter y int; 8:barycenter z int
        arrayIndex=0;
        double[][] Paramarray=new double [ID][9]; //ME: ID == the number of different structures
        for (z=1; z<=NbSlices; z++){
            for (y=0; y<Height; y++){
                for (x=0; x<Width; x++){
                    index=tag[arrayIndex]; //which ID this pixel has
                    val=pict[arrayIndex]; //the value of this pixel
                    Paramarray[index][0]++; //increase this structure's volume
                    if (surf[arrayIndex]) Paramarray[index][1]++; //increase this structure's surface area.
                    Paramarray[index][2]+=val; //increase structure's total intensity
                    Paramarray[index][3]+=x; //for geometric center of structure // I CARE ABOUT THIS BIT
                    Paramarray[index][4]+=y;
                    Paramarray[index][5]+=z;
                    Paramarray[index][6]+=x*val; //for intensity-weighted center of structre
                    Paramarray[index][7]+=y*val;
                    Paramarray[index][8]+=z*val;
                    arrayIndex++;
                }
            }
        }
		
        double voxCount, intensity;
		//ID is still the number of different structures
        for (i=0;i<ID;i++){
            voxCount = Paramarray[i][0];
            intensity = Paramarray[i][2]; // sum over all intensity values
            if (voxCount>=minSize && voxCount<=maxSize) {
                if (voxCount!=0){
                    Paramarray[i][2] /= voxCount; //mean intensity
                    Paramarray[i][3] /= voxCount; //mean x position
                    Paramarray[i][4] /= voxCount; //mean y position
                    Paramarray[i][5] /= voxCount; //mean z position
                }
                if (intensity!=0){
                    Paramarray[i][6] /= intensity; //intensity-weighted mean x pos
                    Paramarray[i][7] /= intensity; // ""y
                    Paramarray[i][8] /= intensity; // ""z
                }
            } else {
                for (j=0;j<9;j++) Paramarray[i][j]=-1;
            }
        }

        //Log data
		
		//ME: Here, I can find the results and use them!!!
		//Need to find the most central structure (with means) -- 
		//then need to go through and find a way to change the selection to be just that structure.
		
		//my code.
		double[] distanceFromCenter = new double[ID];
		Rectangle r = focusArea.getBounds();
		
		double originalCenterX = Width/2;
		double originalCenterY = Height/2;
		for (int i1 = 1; i1 < ID; i1++) {
			int centerX = new Double(Paramarray[i1][3]).intValue();
			int centerY = new Double(Paramarray[i1][4]).intValue();
			if (centerX == -1 || centerY == -1) {
				distanceFromCenter[i1] = -1;
				continue;
			}
			System.out.println("Structure with ID " + i1 + ", has " + Paramarray[i1][0] + " pixels");
			distanceFromCenter[i1] = Math.sqrt(Math.pow(centerX - originalCenterX, 2) + Math.pow(centerY - originalCenterY, 2));
		}
		
		int closestIDToCenter = -1;
		double closestDistance = Width+Height; //any structure couldn't be further away from center than this.
		for (int i2 = 1; i2 < ID; i2++) {
			if (distanceFromCenter[i2] == -1) {
				continue;
			}
			if (distanceFromCenter[i2] <= closestDistance) {
				if (distanceFromCenter[i2] == closestDistance) {
					System.out.println("Two objects have the same distance from the mean - incredible - value is " + closestDistance);
					System.out.println("Width: " + Width + ", Height: " + Height + ", " + distanceFromCenter[0] + ", " + distanceFromCenter[1]);
				}
				else {
					closestIDToCenter = i2;
					closestDistance = distanceFromCenter[i2];
				}
			}
		}
		
		//closestIDToCenter is now the ID of the structure closest to the mean.
		//need to create a selection containing only the pixels in this structure.
		for (int i3 = 0; i3 < pict.length; i3++) {
			int whiteColor = 255;
			int blackColor = 0;
			if (tag[i3] == closestIDToCenter) {
				pict[i3] = whiteColor;
			}
			else {
				pict[i3] = blackColor;
			}
		}
				
		byte[] pixels = new byte[pict.length];
		for (int i4 = 0; i4 < pict.length; i4++) {
			pixels[i4] = (byte) pict[i4];
			if (pixels[i4] == -1) {
			}
		}
		ip.setPixels(pixels);
		iPlus = new ImagePlus("ip", ip);
		
		Roi selectionRoi = selectFromMask(iPlus);
		
		return selectionRoi;
    }
		
	//also from Object_Counter3D
	public boolean withinBounds(int m,int n,int o) {
        return (m >= 0 && m < Width && n >=0 && n < Height && o > 0 && o <= NbSlices );
    }
	
	//also from Object_Counter3D
	public int offset(int m,int n,int o) {
        return m+n*Width+(o-1)*Width*Height;
    }
	
	//also from Object_Counter3D
	public void replacetag(int m,int n){
        for (int i=0; i<tag.length; i++) if (tag[i]==m) tag[i]=n;
    }
	
	
	
	void showAbout() {
		IJ.showMessage("About Segmentation_...", "Attempt 1 -- method copied as closely as possible from Laura and Dan's.");
	}
	
	//Called by the system when the plugin is run. [arg is selected by the user at that time]
	public int setup(String arg, ImagePlus imp) {
		if (arg.equals("about")) {
			showAbout();
			return DONE; //These enums are defined in PlugInFilter.
		}
		return DOES_8G+DOES_STACKS+SUPPORTS_MASKING;
	}
}
