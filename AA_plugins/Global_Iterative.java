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
import ij.gui.PointRoi;
import externalPluginCopies.*;
import externalPluginCopies.FilterPlugin.FilterType;

	

public class Global_Iterative extends SegmentationPlugin implements PlugInFilter {
	
	static final int MED_RD = 5;

	double gauss_mean;
	double gauss_std;
	ImageProcessor ipCopy;
	Roi stackRoi;
	int EDT_Threshold;

	public Global_Iterative() {
		super();
		
		//gauss_mean=84;
		//gauss_std = 4.5;
		//Image B... I think.
		//gauss_mean = 126;
		//gauss_std = 18;
		gauss_std = 10;
		gauss_mean = 101;
		EDT_Threshold = 5;
	}
	
	public Global_Iterative(ImagePlus image, Roi stackRoi, Roi rootRoi, float stdDev, int EDT_Threshold) {
		super();
		this.stackRoi = stackRoi;
		this.setup("", image);
		
		this.gauss_std = stdDev;
		this.EDT_Threshold = EDT_Threshold;
		findNewMean(new ImagePlus("ip", image.getStack().getProcessor(1)), rootRoi);
		//this.run();
	}

	public Global_Iterative(ImagePlus image, Roi stackRoi, Roi rootRoi, int topSlice, int bottomSlice) {
		super();
		this.stackRoi = stackRoi;
		this.setup("", image);
		
		this.gauss_std = 10;
		this.EDT_Threshold = 5;
		
		findNewMean(new ImagePlus("ip", image.getStack().getProcessor(1)), rootRoi);
		ImageStack stack = image.getStack();
		int bottomSliceRelativeToStackEnd = stack.getSize() - bottomSlice;
		for (int i = 1; i < topSlice; i++) {
			stack.deleteSlice(1);
		}
		for (int i = 0; i < bottomSliceRelativeToStackEnd; i++) {
			stack.deleteSlice(stack.getSize());
		}

		this.image = new ImagePlus("Global_Iterative", stack);
		this.run();
	}
	
	public void findNewMean(ImagePlus original, Roi roi) {
		
		selectionPlugin.applyRoi(original, roi, stackRoi);
		
		int measurements = Measurements.MEAN + Measurements.AREA; //+ Measurements.STD_DEV;
		
		ResultsTable rt = new ResultsTable();
		Analyzer an = new Analyzer(original, measurements, rt);
		
		an.measure();
		double newMean = rt.getValue("Mean", rt.getCounter()-1);
		//System.out.println(rt.getValue("Area", rt.getCounter()-1));
		//double newStd = rt.getValue("StdDev", rt.getCounter()-1);
		
		gauss_mean = newMean;
		//gauss_std = newStd;
		System.out.println("Image: " + sliceNumber + ", Mean: " + gauss_mean + ", Std_dev: " + gauss_std);
		
		
		original.deleteRoi();
	}
	
	public void run() {

		this.duplicateImage();
		//this.duplicateImage.show();

		//System.out.println("In Global_Iterative - Image is " + this.image + "\n" +
		//	"First pixel is " + ((byte[]) this.image.getStack().getProcessor(1).getPixels())[0]);

		ImageProcessor ip;
		System.out.println("Beginning Global Iterative process");
		
		ImageStack stack = image.getStack();
		int stackSize = stack.getSize();
		for (sliceNumber = 1; sliceNumber <= stackSize; sliceNumber++) {
			ImageProcessor nextSlice = stack.getProcessor(sliceNumber);
			//System.out.println("Width, height is " + nextSlice.getWidth() + "," + nextSlice.getHeight());
			calculate(nextSlice);
			nextSlice.invert();
		}
		
		stack = image.getStack();
		System.out.println("after calculate for each slice");
		(new ImagePlus("after calculate for each slice", stack)).show();
		EDT edt = new EDT();
		image = edt.performTransform(image); //returns a new object - a float image.
		stack = image.getStack();
		ImageStack byteStack = new ImageStack(X, Y, Z);
		for (sliceNumber = 1; sliceNumber<= stackSize; sliceNumber++) {
			ImageProcessor nextSlice = stack.getProcessor(sliceNumber);
			ip = nextSlice.convertToByteProcessor(true);
			byteStack.setProcessor(ip, sliceNumber);
			corePlugin.applyThreshold(ip, 0, 15);

			//(new ImagePlus("Slice " + sliceNumber, ip)).show();
		}
		image = new ImagePlus("distance transformed", byteStack);
		(new ImagePlus("After calculating Distance transform", byteStack)).show();
		
		Roi centralRoi = selectionPlugin.selectCentralObject(image);
		java.awt.Point contained = centralRoi.getContainedPoints()[0];
		PointRoi point = new PointRoi(contained.x, contained.y);
		
		image.setRoi(point);
		//image.show();
		FindConnectedRegions fcr = new FindConnectedRegions();
		image = fcr.calculate(image);
		//image.show();
		
		stack = image.getStack(); //all images are 16 bits.
		byteStack = new ImageStack(X, Y, Z);
		for (sliceNumber = 1; sliceNumber <= stackSize; sliceNumber++) {
			ImageProcessor nextSlice = stack.getProcessor(sliceNumber);
			ip = nextSlice.convertToByteProcessor(true);
			byteStack.setProcessor(ip, sliceNumber);
			corePlugin.applyThreshold(ip, 1, 255); //original image returns binary pixel values.
		}
		image = new ImagePlus("Final display after automatic connectedRegions on central object", byteStack);
		image.show();
		
		//corePlugin.applyThreshold(ip, 0, 11);
	}
	
	public void run(ImageProcessor ip) {
		this.run();
	}

	public void calculate(ImageProcessor ip) {
		
		//System.err.println("Ooh look I'm running its me I'm running ooh look");
		ipCopy = (ImageProcessor) ip.clone();
		ipCopy.setPixels(ip.getPixelsCopy());
		

		corePlugin.runGaussianMask(ip, gauss_mean, gauss_std);
		filterPlugin.applyFilter(ip, MED_RD, FilterType.MEDIAN);
		//(new ImagePlus("Slice " + sliceNumber, ip)).show();
		corePlugin.applyThreshold(ip, 75, 255);
		filterPlugin.applyFilter(ip, 2, FilterType.MIN);


		ImagePlus iPlus = new ImagePlus("ip" + sliceNumber, ip);
		ImagePlus iPlusCopy = new ImagePlus("ipcopy" + sliceNumber, ipCopy);

		//iPlus.show();
		
		//iPlus.show();

		Roi selectionRoi = selectionPlugin.selectFromMask(iPlus);
		
		//Normal -- System.out.println("Width, height -  is " + iPlus.getWidth() + "," + iPlus.getHeight());
		
		findNewMean(iPlusCopy, selectionRoi);
		
	}
	
	
		
	public void showAbout() {
		IJ.showMessage("About Segmentation_...", "Attempt 1 -- method copied as closely as possible from Laura and Dan's.");
	}

	public void setImageTitle() {
		this.image.setTitle("Global_Iterative Image");
	}
	
}