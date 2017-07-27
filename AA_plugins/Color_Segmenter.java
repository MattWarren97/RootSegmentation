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

import java.util.*;

public class Color_Segmenter extends SegmentationPlugin implements PlugInFilter {
	


	static int[] lut;
	static {
		lut = new int[256];
		for (int i = 0; i< 256; i++) {
			lut[i] = i/16;
		}
	}

	ArrayList<Cluster> clusters;
	ArrayList<Point> toBeProcessed;
	ArrayList<Point> sameClusterToBeProcessed;

	Point[][] points;

	public void run(ImageProcessor ip) {
		ImageStack stack = this.image.getStack();
		for (int i = 1; i <= stack.getSize(); i++) {
			ImageProcessor nextSlice = stack.getProcessor(i);
			convertTo4Bit(nextSlice);
			connectivityAnalysis(nextSlice);
		}
		ImagePlus image = new ImagePlus("4bitimage", stack);
		image.show();
	}

	public void convertTo4Bit(ImageProcessor ip) {
		//copying some lines from the threshold code (https://imagej.nih.gov/ij/source/ij/plugin/Thresholder.java)
		ip.applyTable(Color_Segmenter.lut);
	}



	public void connectivityAnalysis(ImageProcessor ip) {
		int xMin = 0;
		int yMin = 0;
		int xMax = X-1;
		int yMax = Y-1;
		clusters = new ArrayList<Cluster>();
		toBeProcessed = new ArrayList<Point>();
		sameClusterToBeProcessed = new ArrayList<Point>();

		points = new Point[xMax+1][yMax+1];
		for (int i = 0; i < xMax+1; i++) {
			for (int j = 0; j < yMax+1; j++) {
				points[i][j] = new Point(i, j, ip.get(i, j));
			}
		}

		toBeProcessed.add(points[0][0]);

		while(toBeProcessed.size() != 0 || sameClusterToBeProcessed.size() != 0) {
			boolean sameCluster = false;
			Point currentPoint;
			if (sameClusterToBeProcessed.size() != 0) {
				currentPoint = sameClusterToBeProcessed.get(0);
				sameCluster = true;
			}
			else {
				currentPoint = toBeProcessed.get(0);
				this.clusters.add(new Cluster(currentPoint));
			}

			//first try to the right of this point --
			if(currentPoint.x + 1 <= xMax) {
				considerPoint(currentPoint.x+1, currentPoint.y, currentPoint);
			}
			//next try the point to the left of this point --
			if (currentPoint.x - 1  >= xMin) {
				considerPoint(currentPoint.x-1, currentPoint.y, currentPoint);
			}
			//next try below this point --
			if (currentPoint.y + 1 <= yMax) {
				considerPoint(currentPoint.x, currentPoint.y+1, currentPoint);
			}
			//finally try above this point --
			if (currentPoint.y - 1 >= yMin) {
				considerPoint(currentPoint.x, currentPoint.y-1, currentPoint);
			}

			//then remove that last point from its list.
			if (sameCluster) {
				sameClusterToBeProcessed.remove(0);
			}
			else {
				toBeProcessed.remove(0);
			}
		}

		//now need to go through and sort out 'future neighbours'
		System.out.println("There are " + points.length*(points[0].length) + " points.");
		System.out.println("There are " + clusters.size() + " cluster.");
	}

	public void considerPoint(int x, int y, Point previous) {
		Point newPoint = points[x][y];
		if (newPoint.value == previous.value) {
			previous.cluster.addPoint(newPoint);
			sameClusterToBeProcessed.add(newPoint);
		}
		else {
			previous.cluster.addNeighbour(newPoint);
			toBeProcessed.add(newPoint);
		}

	}

}

class Point {
	public int x;
	public int y;
	public int value;
	public Cluster cluster;
	public boolean hasCluster;

	public Point (int x, int y, int v) {
		this.x = x;
		this.y = y;
		this.setValue(v);
	}

	public void setValue(int v) {
		this.value = v;
	}

	public void setCluster(Cluster c) {
		this.cluster = c;
		this.hasCluster = true;
	}
}



class Cluster {
	public ArrayList<Point> points;
	public ArrayList<Cluster> neighbours;
	private ArrayList<Point> futureNeighbours;
	public Cluster(Point p) {
		this.points = new ArrayList<Point>();
		this.neighbours = new ArrayList<Cluster>();
		this.futureNeighbours = new ArrayList<Point>();
		this.addPoint(p);
	}

	public void addPoint(Point p) {
		if (!this.points.contains(p)) {
			this.points.add(p);
			p.setCluster(this);
		}
	}

	public void addNeighbour(Point p) {
		this.futureNeighbours.add(p);
		// if (p.hasCluster) {
		// 	if (!this.neighbours.contains(p.cluster)) {
		// 		this.neighbours.add(p.cluster);
		// 	}
		// }
		// else {
		// 	if (!this.futureNeighbours.contains(p)) {
		// 		this.futureNeighbours.add(p);
		// 	}
		// }
	}
}
