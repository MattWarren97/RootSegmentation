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

public class Global_Threshold extends SegmentationPlugin implements PlugInFilter {
	
	ImagePlus image;
	
	public Global_Threshold(ImagePlus image) {
		super();
		this.image = image;
		run(this.image.getProcessor());
	}
	
	
	public void run(ImageProcessor ip) {
		System.out.println("Begin Global Thresholding");
		ImageConverter ic = new ImageConverter(this.image);
		ic.convertToGray8();
		
		for (int i = 1; i <= image.getStack().getSize(); i++) {
			ImageProcessor nextSlice = this.image.getStack().getProcessor(i);
			corePlugin.applyThreshold(nextSlice, 140, 205);
		}
		this.image.show();
		
		System.out.println("Now cleaning up!");
		try {
			Thread.sleep(5000);
		}
		catch (Exception e) {}
		
		filterPlugin.erode3d(this.image);
		
		this.image.show();
	}
		
	
	public void showAbout() {
		System.out.println("Applies a threshold to the entire image");
	}
	
}