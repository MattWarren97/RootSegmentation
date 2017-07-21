package externalPluginCopies;

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
import AA_plugins.*;

public class CorePlugin {
	
	public void runGaussianMask(ImageProcessor ip, double gauss_mean, double gauss_std) {
		//It would be quicker to find the gaussianValue for each pixel, then decide whether it meets the threshold criteria and create the mask pixel by pixel.
		//but I'm not quite sure how to do that, so will do it here in two steps (threshold, mask) that both need to create their own image.
		

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
	
	public void applyThreshold(ImageProcessor ip, int lowLimit, int highLimit) {
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
	
	
}
