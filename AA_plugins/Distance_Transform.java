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
import externalPluginCopies.*;

public class Distance_Transform implements PlugInFilter {
	ImagePlus image;
	static int callCount = 0;
	public Distance_Transform() {
		System.err.println("Initialising log");
	}
	public void run(ImageProcessor ip) {
		callCount++;
		System.out.println("Call count is now " + callCount);
		
		EDTExtra edt = new EDTExtra();
		System.out.println("ip is " + ip);
		ImagePlus iPlus = edt.performTransform(image);
		System.out.println("iPlus's ip is " + iPlus.getProcessor());
		//iPlus.show();
	}
	
	//Called by the system when the plugin is run. [arg is selected by the user at that time]
	public int setup(String arg, ImagePlus imp) {
		this.image = imp;
		if (arg.equals("about")) {
			IJ.showMessage("Implementation of Euclidean Distance Transform.");
			return DONE; //These enums are defined in PlugInFilter.
		}
		return DOES_8G | NO_CHANGES;
	}
}
	