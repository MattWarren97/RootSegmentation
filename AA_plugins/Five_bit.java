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

public class Five_bit extends SegmentationPlugin implements PlugInFilter {
	
	static int[] lut;
	
	static {
		int binsRequired = 32;
		float divisor = (float)256/(float) binsRequired;
		lut = new int[256];
		for (int i = 0; i< 256; i++) {
			lut[i] = (int) ((float)i/divisor);
		}
	}

	public void run(ImageProcessor ip) {
		ImageStack stack = this.image.getStack();
		for (int i = 1; i <= stack.getSize(); i++) {
			ImageProcessor next = stack.getProcessor(i);
			next.applyTable(Five_bit.lut);
		}
	}
}