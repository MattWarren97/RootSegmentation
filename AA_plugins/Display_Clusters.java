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
	HashSet<Point> processed;
	
	public void run(ImageProcessor ip) {
		sliceNumber = this.image.getCurrentSlice();
		ip.applyTable(Color_Segmenter.lut);
		System.out.println("Beginning connectivity analysis");
		connectivityAnalysis(ip);
		System.out.println("Finished!");
	}
	
	//an entirely different implementation...
	public void connectivityAnalysis(ImageProcessor ip) {
		int xMin = 0;
		int yMin = 0;
		int xMax = this.X - 1;
		int yMax = this.Y - 1;
		
		points = new Point[this.X][this.Y];
		for (int i = 0; i <= xMax; i++) {
			for (int j = 0; j <= yMax; j++) {
				points[i][j] = new Point(i, j, ip.get(i, j));
			}
		}
		

		clusterValue_clusters_MAP = new HashMap<Integer, ArrayList<Cluster>>();
		ArrayList<Cluster> clustersAtValue;
		
		for (int clusterValue = ObjectFinder.rootLowerBound + ObjectFinder.clusterDeviation;
					clusterValue <= ObjectFinder.rootUpperBound - ObjectFinder.clusterDeviation; clusterValue++)
		{
			clustersAtValue = new ArrayList<Cluster>();
			pointsAtValue = new ArrayList<Point>();
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
			
			//at this stage all the clusters at this value have been performed.
			//We know want to compute on these -- (Median, Dilate, Fill-holes, erode, watershed) to smooth out and join up close clusters.
			int pointCount = 0;
			for (Cluster c: clustersAtValue) {
				pointCount += c.getArea();
			}
			int[] xPoints = new int[pointCount];
			int[] yPoints = new int[pointCount];
			
			int pointIndex = 0;
			for (Cluster c: clustersAtValue) {
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
			for (int i = 0; i < pointCount; i++) {
				pixelsCopy[yPoints[i]*width + xPoints[i]] = whiteColour;
			}
			
			int totalArea = this.X * this.Y;
			for (int i = 0; i < totalArea; i++) {
				if (pixelsCopy[i] != whiteColour) {
					pixelsCopy[i] = blackColour;
				}
			}
			ImageProcessor displayClusters =  ip.duplicate();
			ImageProcessor originalClusters = ip.duplicate();
			displayClusters.setPixels(pixelsCopy);
			originalClusters.setPixels(displayClusters.getPixelsCopy());
			
			ImagePlus finalClusters = new ImagePlus("Final clusters on slice " + sliceNumber + ", value: " + clusterValue, displayClusters);
			if (clusterValue == 19) {
				(new ImagePlus("Original clusters on slice " + sliceNumber + ", value: " + clusterValue, originalClusters)).show();
			}
			IJ.run(finalClusters, "Median...", "2");
			IJ.run(finalClusters, "Invert", "");
			IJ.run(finalClusters, "Dilate", "");
			IJ.run(finalClusters, "Fill Holes", "");
			IJ.run(finalClusters, "Erode", "");
			IJ.run(finalClusters, "Watershed", "");
			if (clusterValue == 19) {
				finalClusters.show();
			}

			System.out.println("Displaying for " + sliceNumber + ", value: " + clusterValue);
			
			
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