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
	Roi outerRoi;

	public Global_Iterative() {
		super();
		
		//gauss_mean=84;
		//gauss_std = 4.5;
		//Image B... I think.
		//gauss_mean = 126;
		//gauss_std = 18;
		gauss_mean = 73.5;
		gauss_std = 10;
	}
	
	public Global_Iterative(ImagePlus image, Roi initialRoi, Roi outerRoi) {
		super();
		this.outerRoi = outerRoi;
		this.setup("", image);
		gauss_std = 20;
		System.out.println("before findnewmean");
		findNewMean(new ImagePlus("ip", image.getStack().getProcessor(1)), initialRoi);
		System.out.println("after findnewmean");
		run(this.image.getProcessor());
	}
	
	public void findNewMean(ImagePlus original, Roi roi) {
		
		selectionPlugin.applyRoi(original, roi, outerRoi);
		
		int measurements = Measurements.MEAN; //+ Measurements.STD_DEV;
		
		ResultsTable rt = new ResultsTable();
		Analyzer an = new Analyzer(original, measurements, rt);
		
		an.measure();
		double newMean = rt.getValue("Mean", rt.getCounter()-1);
		//double newStd = rt.getValue("StdDev", rt.getCounter()-1);
		
		gauss_mean = newMean;
		//gauss_std = newStd;
		System.out.println("Image: " + sliceNumber + ", Mean: " + gauss_mean + ", Std_dev: " + gauss_std);
		
		
		original.deleteRoi();
	}
	
	public void run(ImageProcessor ip) {
		System.out.println("Beginning Global Iterative process");
		
		ImageStack stack = image.getStack();
		int stackSize = stack.getSize();
		for (sliceNumber = 1; sliceNumber <= stackSize; sliceNumber++) {
			ImageProcessor nextSlice = stack.getProcessor(sliceNumber);
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
		image = new ImagePlus("Final 8-bit display", byteStack);
		image.show();
		
		//corePlugin.applyThreshold(ip, 0, 11);
		
	}
		
	public void calculate(ImageProcessor ip) {
		
		//System.err.println("Ooh look I'm running its me I'm running ooh look");
		ipCopy = (ImageProcessor) ip.clone();
		ipCopy.setPixels(ip.getPixelsCopy());
		
		corePlugin.runGaussianMask(ip, gauss_mean, gauss_std);
		filterPlugin.applyFilter(ip, MED_RD, FilterType.MEDIAN);
		corePlugin.applyThreshold(ip, 75, 255);
		filterPlugin.applyFilter(ip, 2, FilterType.MIN);

		ImagePlus iPlus = new ImagePlus("ip" + sliceNumber, ip);
		ImagePlus iPlusCopy = new ImagePlus("ipcopy" + sliceNumber, ipCopy);
		
		//iPlus.show();

		Roi selectionRoi = selectionPlugin.selectFromMask(iPlus);
		
		findNewMean(iPlusCopy, selectionRoi);
		
	}
	
	
		
	public void showAbout() {
		IJ.showMessage("About Segmentation_...", "Attempt 1 -- method copied as closely as possible from Laura and Dan's.");
	}
	
}