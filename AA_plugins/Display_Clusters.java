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


public class Display_Clusters extends SegmentationPlugin {
	
	Point[][] points;
	HashMap<Integer, ArrayList<Cluster>> clusterValue_clusters_MAP;
	ArrayList<Point> sameClusterToBeProcessed;
	ArrayList<Point> pointsAtValue;
	ArrayList<Cluster> clustersAtValue;
	HashSet<Point> processed;
	int xMin, yMin, xMax, yMax;
	
	public void run(ImageProcessor ip) {
		xMin = 0;
		yMin = 0;
		xMax = this.X-1;
		yMax = this.Y-1;
		sliceNumber = this.image.getCurrentSlice();
		ip.applyTable(Color_Segmenter.lut);
		System.out.println("Beginning connectivity analysis");
		connectivityAnalysis(ip);
		System.out.println("Finished!");
	}
	
	public ImageProcessor highlightClusters(ImageProcessor ip, ArrayList<Cluster> clusters) {
		int pointCount = 0;
		for (Cluster c: clusters) {
			pointCount += c.getArea();
		}
		int[] xPoints = new int[pointCount];
		int[] yPoints = new int[pointCount];
		
		int pointIndex = 0;
		for (Cluster c: clusters) {
			for (Point p: c.points) {
				xPoints[pointIndex] = p.x;
				yPoints[pointIndex] = p.y;
				pointIndex++;
			}
		}
		//PolygonRoi initialClustersRoi = new PolygonRoi(xPoints, yPoints, pointCount, Roi.POLYGON);
		int width = this.X;
		byte[] pixelsCopy = (byte[]) ip.getPixelsCopy();
		byte whiteColour = (byte) 255;
		byte blackColour = (byte) 0;
		int totalArea = this.X * this.Y;
		for (int i = 0; i < totalArea; i++) {
			pixelsCopy[i] = blackColour;
		}
		
		for (int i = 0; i < pointCount; i++) {
			pixelsCopy[yPoints[i]*width + xPoints[i]] = whiteColour;
		}
		

		ImageProcessor processClusters = ip.duplicate();
		processClusters.setPixels(pixelsCopy);
		return processClusters;
	}
		
		
		
		
	public void findClusters(ImageProcessor ip, int clusterValue) {
		
		points = new Point[this.X][this.Y];
		for (int i = 0; i <= xMax; i++) {
			for (int j = 0; j <= yMax; j++) {
				points[i][j] = new Point(i, j, ip.get(i, j));
			}
		}
		
		clustersAtValue = new ArrayList<Cluster>();
		pointsAtValue = new ArrayList<Point>();
		int count = 0;
		for (int i = 0; i <= xMax; i++) {
			for (int j = 0; j <= yMax; j++) {
				if (points[i][j].value == clusterValue) {
					pointsAtValue.add(points[i][j]);
				}
			}
		}
		
		System.out.println("pointsAtValue is set up with size" + pointsAtValue.size());
		
		while(pointsAtValue.size() != 0) {
			Point nextPoint = pointsAtValue.remove(0);
			Cluster currentCluster = new Cluster(nextPoint, sliceNumber);
			sameClusterToBeProcessed = new ArrayList<Point>();
			sameClusterToBeProcessed.add(nextPoint);
			processed = new HashSet<Point>();
			processed.add(nextPoint);
			
			while(!sameClusterToBeProcessed.isEmpty()) {
				nextPoint = sameClusterToBeProcessed.remove(0); //the value at index 0 is returned while being removed.
				if (nextPoint.x - 1 >= xMin) {
					considerPoint(nextPoint.x - 1, nextPoint.y, currentCluster);
				}
				
				if (nextPoint.x + 1 <= xMax) {
					considerPoint(nextPoint.x + 1, nextPoint.y, currentCluster);
				}
				
				if (nextPoint.y - 1 >= yMin) {
					considerPoint(nextPoint.x, nextPoint.y - 1, currentCluster);
				}
				
				if (nextPoint.y + 1 <= yMax) {
					considerPoint(nextPoint.x, nextPoint.y + 1, currentCluster);
				}
				
			}
			//System.out.println("sameClusterToBeProcessed is empty!");
			clustersAtValue.add(currentCluster);
		}
			
	}
	//an entirely different implementation...
	public void connectivityAnalysis(ImageProcessor ip) {
		

		clusterValue_clusters_MAP = new HashMap<Integer, ArrayList<Cluster>>();
		
		for (int clusterValue = ObjectFinder.rootLowerBound + ObjectFinder.clusterDeviation;
					clusterValue <= ObjectFinder.rootUpperBound - ObjectFinder.clusterDeviation; clusterValue++)
		{
			findClusters(ip, clusterValue);
			
			//at this stage all the clusters at this value have been performed.
			//We know want to compute on these -- (Median, Dilate, Fill-holes, erode, watershed) to smooth out and join up close clusters.
			
			ImageProcessor highlightedClusters = highlightClusters(ip, clustersAtValue);
			
			ImagePlus imp = new ImagePlus("Display Processed Clusters", highlightedClusters);
			
			IJ.run(imp, "Median...", "2");
			IJ.run(imp, "Invert", "");
			IJ.run(imp, "Dilate", "");
			IJ.run(imp, "Fill Holes", "");
			IJ.run(imp, "Erode", "");
			IJ.run(imp, "Watershed", "");
			
			if (clusterValue == 19) {
				imp.show();
			}
			
			//TODO: Find the code implementation of watershed -- see if it is possible to convert the resulting data structures into
			//the new cluster list. Do I need to do that?? This seems not too inefficient for now...
			
			findClusters(highlightedClusters, 0);
			
			ArrayList<Cluster> largeClusters = new ArrayList<Cluster>();
			for (Cluster c: clustersAtValue) {
				if (c.getArea() >= Color_Segmenter.minClusterSize) {
					if (clusterValue == 19) {
						System.out.println("Added cluster with size " + c.getArea());
					}
					largeClusters.add(c);
				}
			}
			
			if (clusterValue == 19) {
				ImageProcessor cleanClusters = highlightClusters(highlightedClusters, largeClusters);
				ImagePlus cleanClusterDisplay = new ImagePlus("Filtered clusters", cleanClusters);
				cleanClusterDisplay.show();
			}
			
			System.out.println("On slice: " + sliceNumber + " value: " + clusterValue + " largeClusters has " + largeClusters.size() + " cluster");
			clusterValue_clusters_MAP.put(clusterValue, largeClusters);
		}
			
		
	}
	
	public void considerPoint(int x, int y, Cluster c) {
		Point newPoint = points[x][y];
		if (processed.contains(newPoint)) {
			return;
		}
		int valueDifference = Math.abs(newPoint.value - c.value);
		if (valueDifference <= ObjectFinder.clusterDeviation) {
			sameClusterToBeProcessed.add(newPoint);
			if (valueDifference == 0) {
				pointsAtValue.remove(newPoint);
				c.addPoint(newPoint, true);
			}
			else {
				c.addPoint(newPoint, false);
			}
		}
		processed.add(newPoint);
	}
}