package AA_plugins;

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
import externalPluginCopies.*;
import externalPluginCopies.FilterPlugin.FilterType;

public class Local_Iterative extends SegmentationPlugin implements PlugInFilter {
	
	static final int ENLARGE_FACTOR = 10;
	static final int MED_RD = 5;

	int xStart;
	int yStart;
	int zStart;

	double gauss_mean;
	double gauss_std;
	double prevArea;
	static int[] areas;
	
	Roi focusArea;
	int measurements;
	

	
	
	public Local_Iterative() {
		super();
		gauss_std = 4.5;
		prevArea = 0;
		measurements = Measurements.MEAN + Measurements.STD_DEV + Measurements.AREA;

		//bottom right - mostly works well.
		/*gauss_mean = 84;
		xStart = 269;
		yStart = 305;
		zStart = 82;
		focusArea = new Roi(new Rectangle(0, 0, 15, 22));*/
				
		//top left - works very well
		gauss_mean = 85;
		xStart = 235;
		yStart = 222;
		focusArea = new Roi(new Rectangle(0, 0, 17, 13));
		zStart = 98;
		
		//bottom left - works not too badly
		/*gauss_mean = 85;
		focusArea = new Roi(new Rectangle(0, 0, 22, 22));
		xStart = 218;
		yStart = 298;
		zStart = 92;*/
		
		//top right, long shape - works terribly
		//gauss_mean = 104;
		//focusArea = new Roi(new Rectangle(0, 0, 31, 12));
		//xStart = 329;
		//yStart = 181;
		//zStart = 169;
		
		//impossible shape... middle right.
		//gauss_mean = 90;
		//focusArea = new Roi(new Rectangle(0,0, 22, 12));
		//xStart = 278;
		//yStart = 259;
		//zStart = 127;
		
		//the top right long shape in resliced 613_300_613.
		/*gauss_mean = 96.6;
		focusArea = new Roi(new Rectangle(0,0,5,9));
		xStart = 267;
		yStart = 155;
		zStart = 216;*/
		

	}

	public void run(ImageProcessor ip) {
		
		areas =  new int[Z-zStart+1];
		
		
		
		ImageStack stack = image.getStack();
		int stackSize = stack.getSize();
		
		//get the user to select an inital location (and slice) of a root leaving the stem.
		//set zTarget to the Z location
		//set focusArea to the initialArea they selected
		
		for (sliceNumber = zStart; sliceNumber <= stackSize; sliceNumber++) {
			ImageProcessor nextSlice = stack.getProcessor(sliceNumber);
			
			System.out.println("Image " + sliceNumber + ":");
			
			Rectangle bounds = focusArea.getBounds();
			System.out.println("Before enlargment, focus area bounds are " + bounds.getWidth() + "," + bounds.getHeight() + " - total area of " + (bounds.getWidth()*bounds.getHeight()));
			focusArea.setLocation(xStart, yStart);
			
			try {
				focusArea = selectionPlugin.enlargeRoi(focusArea, ENLARGE_FACTOR);
				//TODO - I think enlargeRoi might increase the size of the image (instead of 'out of bounds'... Don't want that.
			} catch (Exception e) {
				System.err.println("Selection on image " + sliceNumber + " was too small to enlarge -- exception thrown");
			}
			
			ImageProcessor measureCopy = (ImageProcessor) ip.clone();
			measureCopy.setPixels(ip.getPixelsCopy());
			measureCopy.setRoi(focusArea);
			
			measureCopy = measureCopy.crop();
			measureCopy.resetRoi();
			
			ImagePlus measurePlus = new ImagePlus("measurementsCopy" + sliceNumber, measureCopy);
			ImagePlus transformed;
			
			Roi selectionRoi;		
			ResultsTable rt;
			Analyzer an;
			double newMean;
			double newStd;
			double area = 0;
			int repeatCount = 0;
			
			while(true) {
				if (repeatCount > 0) {
					System.err.println("on image " + sliceNumber + ", loop has been called " + repeatCount + " times.");
					if (repeatCount >= 4) {
						System.err.println("repeatCount reached 4 - failed.");
						newMean = gauss_mean;
						selectionRoi = focusArea;
						break;
					}
				}
				//while the area is not 'acceptable'.
				//measurePlus is the untouched copy.
				measurePlus.deleteRoi();
				transformed = performTransformations(measureCopy);
				selectionRoi = selectionPlugin.selectCentralObject(transformed);
				
				applyRoi(measurePlus, selectionRoi, (int) selectionRoi.getXBase(), (int) selectionRoi.getYBase());
				
				rt = new ResultsTable();
				an = new Analyzer(measurePlus, measurements, rt);
				an.measure();
				
				newMean = rt.getValue("Mean", rt.getCounter()-1);
				newStd = rt.getValue("StdDev", rt.getCounter()-1);
				area = rt.getValue("Area", rt.getCounter()-1);
				System.out.println("Image: " + sliceNumber + ", AREA is " + area + ", mean is " + newMean + ", Measured Std_dev is " + newStd);
				
		
				if (area <= (1/2) * prevArea) {
					gauss_std += 2;
					repeatCount++;
					continue;
				}
				int index = sliceNumber-zStart;
				if (index >= 3) {
					if (area <= (1/2) * areas[index-2]) {
						gauss_std += 2;
						repeatCount++;
						continue;
					}
					if (area <= (1/3) * areas[index-3]) {
						gauss_std += 2;
						repeatCount++;
						continue;
					}
				}
				break;
			}
			
			gauss_mean = newMean;

			//System.out.println("Image: " + sliceNumber + ", Mean: " + gauss_mean + ", Std_dev: " + gauss_std);
			//System.out.println("Image: " + sliceNumber + ", Area: " + area + " change: " + (area-prevArea) + ", as percentage: " + Math.abs((area-prevArea)/prevArea));
			prevArea = area;
			areas[sliceNumber-zStart] = (int) area;
			
			//displaying the changes on the imageprocessor that was passed in.
			Roi offsetRoi = (Roi) selectionRoi.clone();
			offsetRoi.setLocation(xStart, yStart);
			try {
				ip.setRoi(offsetRoi);
			} catch (Exception e) {
				System.err.println("Width and Height were probably 0..., on image " + sliceNumber);
			}
			
			System.out.println("the roi being added to this image is x,y,wid,hei: " + xStart + "," + yStart + "," + offsetRoi.getBounds().getWidth() + "," + offsetRoi.getBounds().getHeight());
			ip.setColor(Color.WHITE);
			ip.fill();
			
			ImagePlus iPlus = new ImagePlus("" + sliceNumber, ip);
			iPlus.show();

			
			focusArea = selectionRoi;
			//System.out.println("new focus area was- X: " + ((int) focusArea.getXBase()) + ", Y: " + ((int) focusArea.getYBase()));
			xStart -= (ENLARGE_FACTOR-focusArea.getXBase());
			yStart -= (ENLARGE_FACTOR-focusArea.getYBase());
			//System.out.println("new xStart, yStart is " + xStart + ", " + yStart);

			measurePlus.deleteRoi();	
			try {
				Thread.sleep(5000);
			}
			catch (Exception e) {
			}
		}
	}
	
	private ImagePlus performTransformations(ImageProcessor original) {
		ImageProcessor copy = (ImageProcessor) original.clone();
		copy.setPixels(original.getPixelsCopy());
		runGaussianMask(copy);
		
		filterPlugin.applyFilter(copy, MED_RD, FilterType.MEDIAN);
		applyThreshold(copy, 75, 255);
		//applyFilter(copy, MED_RD, FilterType.MEDIAN);
		
		return new ImagePlus("ip" + sliceNumber, copy);
	}
		
	
	
	
	
	private void applyRoi(ImagePlus iPlus, Roi roi, int xOffset, int yOffset) {
		roi.setLocation(xOffset, yOffset);
		iPlus.setRoi(roi);
	}
			
	
	private void runGaussianMask(ImageProcessor ip) {
		//It would be quicker to find the gaussianValue for each pixel, then decide whether it meets the threshold criteria and create the mask pixel by pixel.
		//but I'm not quite sure how to do that, so will do it here in two steps (threshold, mask) that both need to create their own image.
		

		//System.err.println("Call count is aaa " + sliceNumber);
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
	
	public void showAbout() {
		IJ.showMessage("About Segmentation_...", "Attempt 1 -- method copied as closely as possible from Laura and Dan's.");
	}
	
}
