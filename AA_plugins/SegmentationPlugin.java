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

public abstract class SegmentationPlugin implements PlugInFilter, Runnable {
	
	ImagePlus image;
	ImagePlus duplicateImage;
	public int X;
	public int Y;
	public int Z;
	public int sliceNumber;
	SelectionPlugin selectionPlugin;
	FilterPlugin filterPlugin;
	CorePlugin corePlugin;
	
	public SegmentationPlugin() {
		System.err.println("Initialising log");
		corePlugin = new CorePlugin();
		selectionPlugin = new SelectionPlugin();
		filterPlugin = new FilterPlugin();
	}
	
	//Called by the system when the plugin is run. [arg is selected by the user at that time]
	public int setup(String arg, ImagePlus imp) {
		this.image = imp;
		this.X = image.getWidth();
		this.Y = image.getHeight();
		this.Z = image.getStackSize();
		
		if (arg.equals("about")) {
			showAbout();
			return DONE; //These enums are defined in PlugInFilter.
		}
		return DOES_8G+SUPPORTS_MASKING;
	}
	
	public abstract void run();
	
	public void showAbout() {

	}
	public void updateImage(boolean duplicate) {
		ImagePlus currentImage = WindowManager.getCurrentImage();
		if (currentImage == null) {
			System.err.println("current image is null - failed.");
			return;
		}
		System.out.println("Running setup with image of size: " + currentImage.getStackSize());
		this.setup("", currentImage);
		if (duplicate) {
			this.duplicateImage();
		}
	}
	
	public void duplicateImage() {
		this.duplicateImage = this.image.duplicate();
		this.duplicateImage.setTitle("Original Image");
		System.out.println("Duplicated image");
	}
	
	
}