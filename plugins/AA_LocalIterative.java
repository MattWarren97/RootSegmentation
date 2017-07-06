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
	//idea is to use ip.clone() -- creates an ip that shares the same pixel array, then use the getPixelsCopy() method and set the new ipcopy to that.
	//then try to mask on that, so I can implement the adjustGaussMeanStd method...
	Roi focusArea;
	
	
	public AA_LocalIterative() {
		System.err.println("Initialising log");
		callCount = 0;
		gauss_mean = 104;
		gauss_std = 6;
		focusArea = new Roi(new Rectangle(0, 0, 31, 12));
		xStart = 329;
		yStart = 181;
		zStart = 169;
		/*focusArea = new Roi(new Rectangle(0, 0, 22, 22));
		xStart = 218;
		yStart = 298;
		zStart = 92;
		*/
		
		
	}

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
		focusArea = enlargeRoi(focusArea);
		//System.out.println("X: " + focusArea.getXBase() + ", Y: " + focusArea.getYBase());
		//Rectangle r = focusArea.getBounds();
		
		
		//It's possible that x and y / x+width or y+height could go out of the image bounds -- TODO check for this.
		//r.setBounds((int) r.getX()- DIMENSIONS_INCREMENT/2+xStart, (int) r.getY()- DIMENSIONS_INCREMENT/2+yStart, (int) r.getWidth()+ DIMENSIONS_INCREMENT, (int) r.getHeight()+ DIMENSIONS_INCREMENT);
		//focusArea = new Roi(r);
		
		//System.out.println("focusArea updated- X: " + ((int) r.getX()) + ", Y: " + ((int) r.getY()) + ", Width: " + ((int) r.getWidth()) + ", Height: " + ((int) r.getHeight()));
		System.out.println("focusArea updated");
		
		
		ip.setRoi(focusArea);
		
		ip = ip.crop();
				
		ipCopy=(ImageProcessor) ip.clone();
		ipCopy.setPixels(ip.getPixelsCopy());
		ImagePlus iPlusCopy = new ImagePlus("ipcopy" + callCount, ipCopy);

		runGaussianMask(ip);
		medianFilter(ip, MED_RD);
		applyThreshold(ip, 75, 255);
		medianFilter(ip, MED_RD);
		
		ImagePlus iPlus = new ImagePlus("ip" + callCount, ip);
		iPlus.show();
		try {
			Thread.sleep(4000);
		} catch(Exception e) {}
		
		Roi selectionRoi = selectFromMask(iPlus);
		applyRoi(iPlusCopy, selectionRoi);
		
		int measurements = Measurements.MEAN + Measurements.STD_DEV;
		
		ResultsTable rt = new ResultsTable();
		Analyzer an = new Analyzer(iPlusCopy, measurements, rt);
		
		an.measure();
		double newMean = rt.getValue("Mean", rt.getCounter()-1);
		double newStd = rt.getValue("StdDev", rt.getCounter()-1);
		
		gauss_mean = newMean;
		//gauss_std = newStd;
		//if (gauss_std > 4.5) {
			//gauss_std = 4.5;
		//}
		System.out.println("Image: " + callCount + ", Mean: " + gauss_mean + ", Std_dev: " + gauss_std);
		
		focusArea = selectionRoi;
		System.out.println("new focus area was- X: " + ((int) focusArea.getXBase()) + ", Y: " + ((int) focusArea.getYBase()));
		xStart -= (ENLARGE_FACTOR-focusArea.getXBase());
		yStart -= (ENLARGE_FACTOR-focusArea.getYBase());
		iPlusCopy.deleteRoi();
		iPlus.deleteRoi();
		
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
		
		//System.err.println("Image: " + callCount + ", Binary: " + ip.isBinary());
		
		
		
		//We are using Fiji 8 with ImageJ 2.0.0.
		//I can't find sources for ImageJ 2.0.0, only 1.5.
		//In 1.5 the 'isBinary()' method will always return false.
		//In 2.0.0 it will seemingly return true sometimes, but not always, and I don't know what the conditions are.
		//actually, it appears the images were not binary...
		
		
		System.out.println("Binary: " + ip.isBinary() + ", Grayscale: " + ip.isGrayscale() + ", defaultLUT: " + ip.isDefaultLut());
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
		System.out.println("Min: " + ip.getMinThreshold() + ", max: " + ip.getMaxThreshold());
		
		tts.run(ip);

		Roi selectionRoi = iPlus.getRoi();
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
	
	private void applyRoi(ImagePlus iPlus, Roi roi) {
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
	private void objectCounter(ImagePlus iPlus) {
		ImageProcessor ip = iPlus.getProcessor();
        int x, y, z;
        int xn, yn, zn;
        int i, j, k, arrayIndex, offset;
        int voisX = -1, voisY = -1, voisZ = -1;
        int maxX = Width-1, maxY=Height-1;

        int index;
        int val;
        double col;

        int minTag;
        int minTagOld;

        cal = img.getCalibration();
        if (cal == null ) {
            cal = new Calibration(img);
        }
        double pixelDepth = cal.pixelDepth;
        double pixelWidth = cal.pixelWidth;
        double pixelHeight = cal.pixelHeight;
        double zOrigin = cal.zOrigin;
        double yOrigin = cal.yOrigin;
        double xOrigin = cal.xOrigin;
        double voxelSize = pixelDepth * pixelWidth * pixelHeight;

        pict=new int [Height*Width*NbSlices];
        thr=new boolean [Height*Width*NbSlices];
        tag=new int [Height*Width*NbSlices];
        surf=new boolean [Height*Width*NbSlices];
        Arrays.fill(thr,false);
        Arrays.fill(surf,false);

        //Load the image in a one dimension array
		
		//ME: I'm pretty sure this could be done with ip.getPixels();
        ImageStack stack = img.getStack();
        arrayIndex=0;
        for (z=1; z<=NbSlices; z++) {
            ip = stack.getProcessor(z);
            for (y=0; y<Height;y++) {
                for (x=0; x<Width;x++) {
                    PixVal=ip.getPixel(x, y);
                    pict[arrayIndex]=PixVal;
                    if (PixVal>ThrVal){
                        thr[arrayIndex]=true;
                    }
                    arrayIndex++;
                }
            }
        }

		
		//ME: IDs are used to work out what structures are connected...
		//I won't touch this code for now
		
        //First ID attribution
        int tagvois;
        ID=1;
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
        Paramarray=new double [ID][9]; //ME: ID == the number of different structures
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
                for (j=0;j<9;j++) Paramarray[i][j]=0;
            }
        }

        //Log data
		
		//ME: Here, I can find the results and use them!!!
		//Need to find the most central structure (with means) -- 
		//then need to go through and find a way to change the selection to be just that structure.
        rt = new ResultsTable();
        IDarray=new int[ID];

        String[] head={"Volume","Surface","Intensity","Centre X","Centre Y","Centre Z","Centre int X","Centre int Y","Centre int Z"};
        for (i=0; i<head.length; i++) rt.setHeading(i,head[i]);

        k=1;
        for (i=1;i<ID;i++){
            if (Paramarray[i][0]!=0){
                rt.incrementCounter();
                IDarray[i]=k;
                voxCount = Paramarray[i][0];
                rt.addValue(0,voxCount * voxelSize);
                rt.addValue(1,Paramarray[i][1] / voxCount);
                rt.addValue(2,Paramarray[i][2]);
                rt.addValue(3,cal.getX(Paramarray[i][3]));
                rt.addValue(4,cal.getY(Paramarray[i][4]));
                rt.addValue(5,cal.getZ(Paramarray[i][5]-1));
                rt.addValue(6,cal.getX(Paramarray[i][6]));
                rt.addValue(7,cal.getY(Paramarray[i][7]));
                rt.addValue(8,cal.getZ(Paramarray[i][8]-1));
                k++;
            }
        }
		rt.show("Results");

        int nParticles = rt.getCounter();

        /*if (showParticles){ // Create 'Particles' image
            Particles=NewImage.createShortImage("Particles "+imgtitle,Width,Height,NbSlices,0);
            stackParticles=Particles.getStack();
            Particles.setCalibration(cal);
            arrayIndex=0;
            for (z=1; z<=NbSlices; z++){
                ip=stackParticles.getProcessor(z);
                for (y=0; y<Height; y++){
                    for (x=0; x<Width; x++){
                        if (thr[arrayIndex]) {
                            index=tag[arrayIndex];
                            if (Paramarray[index][0]>0){//(Paramarray[index][0]>=minSize && Paramarray[index][0]<=maxSize)
                                col=IDarray[index]+1;
                                ip.setValue(col);
                                ip.drawPixel(x, y);
                            }
                        }
                        arrayIndex++;
                    }
                }
            }
            Particles.show();
            IJ.run("Fire");
        }*/

        /*if (showEdges){ // Create 'Edges' image
            Edges=NewImage.createShortImage("Edges "+imgtitle,Width,Height,NbSlices,0);
            stackEdges=Edges.getStack();
            Edges.setCalibration(cal);
            arrayIndex=0;
            for (z=1; z<=NbSlices; z++){
                ip=stackEdges.getProcessor(z);
                for (y=0; y<Height; y++){
                    for (x=0; x<Width; x++){
                        if (thr[arrayIndex]) {
                            index=tag[arrayIndex];
                            if (Paramarray[index][0]>0 && surf[arrayIndex]) {
                                col=IDarray[index]+1;
                                ip.setValue(col);
                                ip.drawPixel(x, y);
                            }
                        }
                        arrayIndex++;
                    }
                }
            }
            Edges.show();
            IJ.run("Fire");
        }*/

        if (showCentres){ // Create 'Centres' image
            Centres=NewImage.createShortImage("Geometrical Centres "+imgtitle,Width,Height,NbSlices,0);
            stackCentres=Centres.getStack();
            Centres.setCalibration(cal);
            for (i=0;i<nParticles;i++){
                ip=stackCentres.getProcessor((int)Math.round(rt.getValue(5,i)/pixelDepth+zOrigin+1));
                ip.setValue(i+1);
                ip.setLineWidth(DotSize);
                ip.drawDot((int)Math.round(rt.getValue(3,i)/pixelWidth+xOrigin), (int)Math.round(rt.getValue(4,i)/pixelHeight+yOrigin));
            }
            Centres.show();
            IJ.run("Fire");
        }

        /*if (showCentresInt){ // Create 'Intensity weighted Centres' image
            CentresInt=NewImage.createShortImage("Intensity based centres "+imgtitle,Width,Height,NbSlices,0);
            stackCentresInt=CentresInt.getStack();
            CentresInt.setCalibration(cal);
            for (i=0;i<nParticles;i++){
                ip=stackCentresInt.getProcessor((int)Math.round(rt.getValue(8,i)/pixelDepth+zOrigin+1));
                ip.setValue(i+1);
                ip.setLineWidth(DotSize);
                ip.drawDot((int)Math.round(rt.getValue(6,i)/pixelWidth+xOrigin), (int)Math.round(rt.getValue(7,i)/pixelHeight+yOrigin));
            }
            CentresInt.show();
            IJ.run("Fire");
        }*/

        //"Volume","Surface","Intensity","Centre X","Centre Y","Centre Z","Centre int X","Centre int Y","Centre int Z"

        /*if (showNumbers){
            Font font = new Font("SansSerif", Font.PLAIN, FontSize);
            for (i=0;i<nParticles;i++){
                z = (int)Math.round(rt.getValue(5,i)/pixelDepth+zOrigin+1);
                y = (int)Math.round(rt.getValue(4,i)/pixelHeight+yOrigin);
                x = (int)Math.round(rt.getValue(3,i)/pixelWidth+xOrigin);
                if (debug) IJ.log(pluginName + " Draw pixels: slice=" + z + " coords" + x + "," + y);
                if (showParticles){
                    ip=stackParticles.getProcessor(z);
                    ip.setFont(font);
                    ip.setValue(nParticles);
                    ip.drawString(""+(i+1),x,y);
                }
                if (showEdges){
                    ip=stackEdges.getProcessor(z);
                    ip.setFont(font);
                    ip.setValue(nParticles);
                    ip.drawString(""+(i+1),x,y);
                }
                if (showCentres){
                    ip=stackCentres.getProcessor(z);
                    ip.setFont(font);
                    ip.setValue(nParticles);
                    ip.drawString(""+(i+1),x,y);
                }
                if (showCentresInt){
                    ip=stackCentresInt.getProcessor(z);
                    ip.setFont(font);
                    ip.setValue(nParticles);
                    ip.drawString(""+(i+1),x,y);
                }
                IJ.showStatus("Drawing numbers");
                IJ.showProgress(i,nParticles);
            }
        }*/

        /*if (showParticles){
            Particles.getProcessor().setMinAndMax(1,nParticles);
            Particles.updateAndDraw();
        }

        if (showEdges){
            Edges.getProcessor().setMinAndMax(1,nParticles);
            Edges.updateAndDraw();
        }*/

        if (showCentres){
            Centres.getProcessor().setMinAndMax(1,nParticles);
            Centres.updateAndDraw();
        }

        /*if (showCentresInt){
            CentresInt.getProcessor().setMinAndMax(1,nParticles);
            CentresInt.updateAndDraw();
        }

        if (summary){
            double TtlVol=0;
            double TtlSurf=0;
            double TtlInt=0;
            for (i=0; i<nParticles;i++){
                TtlVol+=rt.getValueAsDouble(0,i);
                TtlSurf+=rt.getValueAsDouble(1,i);
                TtlInt+=rt.getValueAsDouble(2,i); //
            }
            int precision = Analyzer.precision;
            IJ.log(imgtitle);
            IJ.log("Number of Objects = " + nParticles);
            IJ.log("Mean Volume = " + IJ.d2s(TtlVol/nParticles,precision));
            IJ.log("Mean Surface Fraction = " + IJ.d2s(TtlSurf/nParticles,precision));
            IJ.log("Mean Intensity = " + IJ.d2s(TtlInt/nParticles,precision));
            IJ.log("Voxel Size = " + IJ.d2s(voxelSize));
        }*/
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
