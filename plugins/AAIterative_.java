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

public class AAIterative_ implements PlugInFilter {
	
	
	static final int MED_RD = 5;
	static final int X = 500;
	static final int Y = 500;
	static final int Z = 300;
	static final int RM_OUT_FILTER = 4;
	static final int[] SD_ARRAY = {4, 5, 6};
	static final int[] ERODE_ARRAY = {19,7,3};
	static final int[] MAX_STD = {8, 9, 10};
	int callCount;
	double gauss_mean;
	double gauss_std;
	ImageProcessor ipCopy;
	//idea is to use ip.clone() -- creates an ip that shares the same pixel array, then use the getPixelsCopy() method and set the new ipcopy to that.
	//then try to mask on that, so I can implement the adjustGaussMeanStd method...

	public AAIterative_() {
		System.err.println("Initialising log");
		callCount = 0;
		gauss_mean = 85;
		gauss_std = 4.5;
	}

	//on an image stack, the run method is called on each image in order.
	public void run(ImageProcessor ip) {
		
		//System.err.println("Ooh look I'm running its me I'm running ooh look");
		callCount++;
		ipCopy = (ImageProcessor) ip.clone();
		ipCopy.setPixels(ip.getPixelsCopy());
		
		runGaussianMask(ip);
		medianFilter(ip, MED_RD);
		applyThreshold(ip, 75, 255);


		ImagePlus iPlus = new ImagePlus("ip" + callCount, ip);
		ImagePlus iPlusCopy = new ImagePlus("ipcopy" + callCount, ipCopy);
		
		//iPlus.show();

		Roi selectionRoi = selectFromMask(iPlus);
		
		applyRoi(iPlusCopy, selectionRoi);


		
		int measurements = Measurements.MEAN + Measurements.STD_DEV;
		
		ResultsTable rt = new ResultsTable();
		Analyzer an = new Analyzer(iPlusCopy, measurements, rt);
		
		an.measure();
		double newMean = rt.getValue("Mean", rt.getCounter()-1);
		double newStd = rt.getValue("StdDev", rt.getCounter()-1);
		
		gauss_mean = newMean;
		gauss_std = newStd;
		if (gauss_std > 10) {
			gauss_std = 10;
		}
		System.out.println("Image: " + callCount + ", Mean: " + newMean + ", Std_dev: " + newStd);
		
		
		iPlusCopy.deleteRoi();
		iPlus.deleteRoi();
		
		
		
		
		/*
		iPlus.getWindow().close();
		iPlusCopy.getWindow().close();
		*/
		
		/*ImageStatistics stats = ImageStatistics.getStatistics(ipCopy, Measurements.MEAN, iPlusCopy.getCalibration());
		System.out.println("Mean is " + stats.mean);
		*/
		/*
		ImageStatistics ipCopyStats = iPlusCopy.getStatistics();		
		double mean = ipCopyStats.MEAN;
		double std = ipCopyStats.STD_DEV;
		System.out.println("IPCopy:   mean: " + mean + ", std: " + std);
		*/		
		
		/*ResultsTable rt = ResultsTable.getResultsTable();
		Analyzer an = new Analyzer(iPlusCopy);
		an.saveResults(*/
		
	}
	
	/*
	private void setSelectedWindow(ImageWindow iw) {
		WindowManager.setCurrentWindow(iw);
		while(WindowManager.getCurrentWindow() != iw) {
			System.out.println("Not changed yet");
		}
	}
	*/
	
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
		//System.err.println("Width: " + r.width + ", Height: " + r.height + ", y: " + r.y + ", x: " + r.x);

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

		int width = X;
		int height = Y;
		
		byte[] pixels = (byte[]) ip.getPixels();
		
		int[] tmp=new int[pixels.length];
		for (int i=0;i<pixels.length;i++)
			tmp[i]=pixels[i]&0xff; //this.... is just pixels[i], surely?
		
		int[][] arrays = create2DIntArray(pixels, width, height);
		
		int [][] medianArray = new int [width][height];
		for(int j=0;j<height;j++)
			for(int i=0;i<width;i++)
				medianArray[i][j] = pixelMedian(arrays,MED_RD,width,height,i,j);
		
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
	
	
	
	private int segmentAndCalculateMean(ImageProcessor ip, float init_mean, float init_std) {
		//change canvas size (for median filter?)
		//set the measurements
		//Duplicate the first slice (twice)
		//run("Macro...", "code=v=(1/("+SD+"*sqrt(2*3.14)))*exp((-(v-"+M+")*(v-"+M+"))/(2*"+SD+"*"+SD+"))/(1/("+SD+"*sqrt(2*3.14)))*exp((0)/(2*("+SD+"*"+SD+")))*255");

		//Run that guassian formula, pixel = (1/(SD*sqrt(2*3.14))) * exp((-(pixel-MEAN)*(pixel-MEAN))/(2*SD*SD)) / (1/(SD*sqrt(2*3.14))) * exp((0/(2*SD*SD))) * 255;
		//simplifies to pixel = exp((-(pixel-MEAN)^2)/2*SD^2) * 255
		
		//then threshold on that (is equivalent to ~1.5xS.D. either side of mean).
		//convert to mask, invert.
		
		//run a median filter
		//remove outliers
		
		return 0;
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
