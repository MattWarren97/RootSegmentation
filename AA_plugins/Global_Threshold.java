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

public class Global_Threshold extends SegmentationPlugin implements PlugInFilter {
	
	//ImagePlus image;
	
	public Global_Threshold(ImagePlus image) {
		super();
		this.setup("", image);
		//this.run();
	}
	
	public void run() {
		System.out.println("Begin Global Thresholding");

		//System.out.println("In Global_Threshold - Image is " + this.image + "\n" +
		//	"First pixel is " + ((byte[]) this.image.getStack().getProcessor(1).getPixels())[0]);
		ImageConverter ic = new ImageConverter(this.image);
		ic.convertToGray8();
		
		for (int i = 1; i <= image.getStack().getSize(); i++) {
			ImageProcessor nextSlice = this.image.getStack().getProcessor(i);
			corePlugin.applyThreshold(nextSlice, 125, 205);
			//filterPlugin.applyFilter(nextSlice, 5, FilterType.MEDIAN);
		}
		this.image.show();
		
		
		System.out.println("Now cleaning up!");
		
		filterPlugin.erode3d(this.image);
		this.image.setStack(Filters3D.filter(this.image.getStack(), Filters3D.MIN, 2, 2, 2));
		this.image.setStack(Filters3D.filter(this.image.getStack(), Filters3D.MAX, 2, 2, 2));
		//try {
		//	Thread.sleep(5000);
		//}
		//catch (Exception e) {}
		

		/*filterPlugin.erode3d(this.image);
		filterPlugin.erode3d(this.image);
		System.out.println("Done eroding, now dilating.");
		filterPlugin.dilate3d(this.image);
		filterPlugin.dilate3d(this.image);*/
		
		this.image.show();
	}
	
	
	public void run(ImageProcessor ip) {
		this.run();
	}
		
	
	public void showAbout() {
		System.out.println("Applies a threshold to the entire image");
	}

	public void setImageTitle() {
		this.image.setTitle("Global_Threshold image");
		//System.out.println("In Threshold: image is " + this.image);
	}

	
	
}