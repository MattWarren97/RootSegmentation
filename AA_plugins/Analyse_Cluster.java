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
import ij.gui.PointRoi;
import ij.measure.Calibration;
import externalPluginCopies.*;
import externalPluginCopies.FilterPlugin.FilterType;

import org.apache.commons.collections4.bidimap.*;

import java.util.*;


public class Analyse_Cluster extends SegmentationPlugin {
	
	int clusterValue;
	
	public void run(ImageProcessor ip) {

		ImageProcessor ipCopy = (ImageProcessor) ip.clone();
		ipCopy.setPixels(ip.getPixelsCopy());
		PointRoi roi = (PointRoi) this.image.getRoi();
		if(roi == null) {
			System.err.println("NO POINT SELECTED. Select and try again.");
			return;
		}
		int roiX = (int) roi.getXBase();
		int roiY = (int) roi.getYBase();
		System.out.println("coords:" + roiX + ", " + roiY);
		
		/*if (xCoordinates.length != 1) {
			System.err.println("Please select exactly one point. Selected: " + xCoordinates.length);
			return;
		}
		*/
		ipCopy.applyTable(Color_Segmenter.lut);
		ArrayList<Pixel> cluster = connectToCluster(ipCopy, roiX, roiY);
		
		byte[] pixels = (byte[]) ipCopy.getPixels();
		int whiteColour = 255;
		for (Pixel p: cluster) {
			pixels[p.y*this.X + p.x] = (byte) whiteColour;
		}
		(new ImagePlus("cluster at value: " + clusterValue + " + on slice " + ip.getSliceNumber(), ipCopy)).show();
	}
	
	public ArrayList<Pixel> connectToCluster(ImageProcessor ip, int x, int y) {
		
		int xMin = 0;
		int yMin = 0;
		int xMax = this.X - 1;
		int yMax = this.Y - 1;
		
		
		Pixel[][] points;
		
		points = new Pixel[xMax+1][yMax+1];
		for (int i = 0; i < xMax+1; i++) {
			for (int j = 0; j < yMax+1; j++) {
				points[i][j] = new Pixel(i, j, ip.get(i, j));
				//System.out.println(ip.get(i, j));
			}
		}
		
		int COLOUR_DIFFERENCE_ALLOWED = 1;
		

		
		ArrayList<Pixel> thisCluster = new ArrayList<Pixel>();
		ArrayList<Pixel> toCheck = new ArrayList<Pixel>();
		toCheck.add(points[x][y]);
		//thisCluster.add(points[x][y]);
		
		//Pixel nextPixel = points[x][y];
		Pixel nextPixel;
		clusterValue = points[x][y].value;
		System.out.println("clusterValue is " + clusterValue + ", x and y are " + x + ", " + y);
		do {
			nextPixel = toCheck.get(0);
			toCheck.remove(0);
			if (thisCluster.contains(nextPixel)) {
				continue;
			}
			//System.out.println("Considering " + nextPixel);
			if (Math.abs(nextPixel.value - clusterValue) <= COLOUR_DIFFERENCE_ALLOWED) {
				//System.out.println("Accepted.");
				thisCluster.add(nextPixel);
		
				if (nextPixel.x - 1 >= xMin) {
					toCheck.add(points[nextPixel.x-1][nextPixel.y]);
				}
				
				if (nextPixel.x + 1 <= xMax) {
					toCheck.add(points[nextPixel.x+1][nextPixel.y]);
				}
				
				if (nextPixel.y - 1 >= yMin) {
					toCheck.add(points[nextPixel.x][nextPixel.y-1]);
				}
				
				if (nextPixel.y + 1 <= yMax) {
					toCheck.add(points[nextPixel.x][nextPixel.y + 1]);
				}
			}
			else {
				//System.out.println("Rejected.");
			}
		}
		while (toCheck.size() != 0);
		
		System.out.println("Cluster size is " + thisCluster.size());
		return thisCluster;
	}
	
		
		
		
}


class Pixel {
	public int x, y;
	public int value;
	public Pixel(int x, int y, int value) {
		this.x = x;
		this.y = y;
		this.value = value;
	}
	
	public String toString() {
		return "Pixel at (" + x + ", " + y + ") with value " + value;
	}
}