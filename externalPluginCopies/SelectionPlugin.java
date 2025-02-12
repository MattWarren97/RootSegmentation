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
import ij.plugin.filter.ThresholdToSelection;
import ij.plugin.filter.EDM;
import ij.gui.*;
import ij.measure.Calibration;

//contains a single method -- to select the central object from a mask.
public class SelectionPlugin {

	//these variables are from Object_Counter3D - but are used in multiple methods.
	int[] tag;
	int Width;
	int Height;
	int NbSlices;
	
	
	
	//lines modified from https://imagej.nih.gov/ij/plugins/track/Object_Counter3D.java
	public Roi selectCentralObject(ImagePlus iPlus) {
		ImageProcessor ip = iPlus.getProcessor();
		int selectedCount = 0;
		byte[] pixs = (byte[]) ip.getPixels();
		for (int i = 0; i < pixs.length; i++) {
			if (pixs[i] == -1) {
				selectedCount++;
			}
		}
		//System.out.println("selectCentralObject, selected count is " + selectedCount);
        int x, y, z;
        int xn, yn, zn;
        int i, j, k, arrayIndex, offset;
        int voisX = -1, voisY = -1, voisZ = -1;
		Width = iPlus.getWidth();
		Height = iPlus.getHeight();
		NbSlices = 1; //TODO do I want this to be larger than 1 ever?? I think not.
        int maxX = Width-1, maxY=Height-1;

		int minSize = 10;
		int maxSize = Width*Height;
		
        int index;
        int val;
        double col;

        int minTag;
        int minTagOld;

		Calibration cal;
        cal = iPlus.getCalibration();
        if (cal == null ) {
            cal = new Calibration(iPlus);
        }
        double pixelDepth = cal.pixelDepth;
        double pixelWidth = cal.pixelWidth;
        double pixelHeight = cal.pixelHeight;
        double zOrigin = cal.zOrigin;
        double yOrigin = cal.yOrigin;
        double xOrigin = cal.xOrigin;
        double voxelSize = pixelDepth * pixelWidth * pixelHeight;

        int[] pict=new int [Height*Width*NbSlices];
        boolean[] thr=new boolean [Height*Width*NbSlices];
        tag=new int [Height*Width*NbSlices];
        boolean[] surf=new boolean [Height*Width*NbSlices];
        Arrays.fill(thr,false);
        Arrays.fill(surf,false);

        //Load the image in a one dimension array
		
		//ME: I'm pretty sure this could be done with ip.getPixels();
        ImageStack stack = iPlus.getStack();
        arrayIndex=0;
		int ThrVal = 0;
        for (z=1; z<=NbSlices; z++) {
            ip = stack.getProcessor(z);
            for (y=0; y<Height;y++) {
                for (x=0; x<Width;x++) {
                    int PixVal=ip.getPixel(x, y);
                    pict[arrayIndex]=PixVal;
					if (PixVal>ThrVal){
						thr[arrayIndex]=true;
						//TODO The current problem is that the black '0 space' is being counted.
						// I need to work uot why and stop it from being counted / stop it from being able to be the closest bit.
                    }
                    arrayIndex++;
                }
            }
        }

		//ME: IDs are used to work out what structures are connected...
		//I won't touch this code for now
		
        //First ID attribution
        int tagvois;
        int ID=1;
        arrayIndex=0;
        for (z=1; z<=NbSlices; z++){
            for (y=0; y<Height; y++){
                for (x=0; x<Width; x++){
                    if (thr[arrayIndex]){
                        tag[arrayIndex]=ID;
                        minTag=ID;
                        i=0;
                        //Find the minimum tag in the neighbours pixels
                        for (voisZ=z-1;voisZ<=z+1;voisZ++){
                            for (voisY=y-1;voisY<=y+1;voisY++){
                                for (voisX=x-1;voisX<=x+1;voisX++){
                                    if (withinBounds(voisX, voisY, voisZ)) {
                                        offset=offset(voisX, voisY, voisZ);
                                        if (thr[offset]){
                                            i++;
                                            tagvois = tag[offset];
                                            if (tagvois!=0 && tagvois<minTag) minTag=tagvois;
                                        }
                                    }
                                }
                            }
                        }
                        if (i!=27) surf[arrayIndex]=true;
                        tag[arrayIndex]=minTag;
                        if (minTag==ID){
                            ID++;
                        }
                    }
                    arrayIndex++;
                }
            }
        }
        ID++;

        //Minimization of IDs=connection of structures
        arrayIndex=0;
        for (z=1; z<=NbSlices; z++){
            for (y=0; y<Height; y++){
                for (x=0; x<Width; x++){
                    if (thr[arrayIndex]){
                        minTag=tag[arrayIndex];
                        //Find the minimum tag in the neighbours pixels
                        for (voisZ=z-1;voisZ<=z+1;voisZ++){
                            for (voisY=y-1;voisY<=y+1;voisY++){
                                for (voisX=x-1;voisX<=x+1;voisX++){
                                    if (withinBounds(voisX, voisY, voisZ)) {
                                        offset=offset(voisX, voisY, voisZ);
                                        if (thr[offset]){
                                            tagvois = tag[offset];
                                            if (tagvois!=0 && tagvois<minTag) minTag=tagvois;
                                        }
                                    }
                                }
                            }
                        }
                        //Replacing tag by the minimum tag found
                        for (voisZ=z-1;voisZ<=z+1;voisZ++){
                            for (voisY=y-1;voisY<=y+1;voisY++){
                                for (voisX=x-1;voisX<=x+1;voisX++){
                                    if (withinBounds(voisX, voisY, voisZ)) {
                                        offset=offset(voisX, voisY, voisZ);
                                        if (thr[offset]){
                                            tagvois = tag[offset];
                                            if (tagvois!=0 && tagvois!=minTag) replacetag(tagvois,minTag);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    arrayIndex++;
                }
            }
		}

        //Parameters determination 
		//0:volume; 1:surface; 2:intensity; 3:barycenter x; 4:barycenter y; 
		//5:barycenter z; 6:barycenter x int; 7:barycenter y int; 8:barycenter z int
        arrayIndex=0;
        double[][] Paramarray=new double [ID][9]; //ME: ID == the number of different structures
        for (z=1; z<=NbSlices; z++){
            for (y=0; y<Height; y++){
                for (x=0; x<Width; x++){
                    index=tag[arrayIndex]; //which ID this pixel has
                    val=pict[arrayIndex]; //the value of this pixel
                    Paramarray[index][0]++; //increase this structure's volume
                    if (surf[arrayIndex]) Paramarray[index][1]++; //increase this structure's surface area.
                    Paramarray[index][2]+=val; //increase structure's total intensity
                    Paramarray[index][3]+=x; //for geometric center of structure // I CARE ABOUT THIS BIT
                    Paramarray[index][4]+=y;
                    Paramarray[index][5]+=z;
                    Paramarray[index][6]+=x*val; //for intensity-weighted center of structre
                    Paramarray[index][7]+=y*val;
                    Paramarray[index][8]+=z*val;
                    arrayIndex++;
                }
            }
        }
		
        double voxCount, intensity;
		//ID is still the number of different structures
        for (i=0;i<ID;i++){
            voxCount = Paramarray[i][0];
            intensity = Paramarray[i][2]; // sum over all intensity values
            if (voxCount>=minSize && voxCount<=maxSize) {
                if (voxCount!=0){
                    Paramarray[i][2] /= voxCount; //mean intensity
                    Paramarray[i][3] /= voxCount; //mean x position
                    Paramarray[i][4] /= voxCount; //mean y position
                    Paramarray[i][5] /= voxCount; //mean z position
                }
                if (intensity!=0){
                    Paramarray[i][6] /= intensity; //intensity-weighted mean x pos
                    Paramarray[i][7] /= intensity; // ""y
                    Paramarray[i][8] /= intensity; // ""z
                }
            } else {
                for (j=0;j<9;j++) Paramarray[i][j]=-1;
            }
        }

        //Log data
		
		//ME: Here, I can find the results and use them!!!
		//Need to find the most central structure (with means) -- 
		//then need to go through and find a way to change the selection to be just that structure.
		
		//my code.
		
		double[] weightedDistanceFromCenter = new double[ID];
		
		double originalCenterX = Width/2;
		double originalCenterY = Height/2;
		double maximumDistance = Math.sqrt(Math.pow(Width/2, 2) + Math.pow(Height/2, 2));
		for (int i1 = 0; i1 < ID; i1++ ){
			int centerX = new Double(Paramarray[i1][3]).intValue();
			int centerY = new Double(Paramarray[i1][4]).intValue();
			if (centerX == -1 || centerY == -1) {
				weightedDistanceFromCenter[i1] = -1;
				continue;
			}
			double distance = Math.sqrt(Math.pow(centerX - originalCenterX, 2) + Math.pow(centerY - originalCenterY, 2));
			double area = Paramarray[i1][0];
			double inverseDistance = (maximumDistance - distance) / maximumDistance;
			weightedDistanceFromCenter[i1] = area*inverseDistance;
		}
		
		int bestStructure = -1;
		double bestWeightedDistance = 0;
		//double closestDistance = Width+Height; //any structure couldn't be further away from center than this.
		for (int i2 = 1; i2 < ID; i2++) {

			if (weightedDistanceFromCenter[i2] > bestWeightedDistance) {
				if (weightedDistanceFromCenter[i2] == bestWeightedDistance) {
					System.out.println("Two objects have the same weighted distance - incredible - value is " + bestWeightedDistance);
					//System.out.println("Width: " + Width + ", Height: " + Height + ", " + distanceFromCenter[0] + ", " + distanceFromCenter[1]);
				}
				else {
					bestStructure = i2;
					bestWeightedDistance = weightedDistanceFromCenter[i2];
				}
			}
		}
		
		//bestStructre is now the ID of the structure with the most area, closest to the mean.
		//need to create a selection containing only the pixels in this structure.
		for (int i3 = 0; i3 < pict.length; i3++) {
			int whiteColor = 255;
			int blackColor = 0;
			if (tag[i3] == bestStructure) {
				pict[i3] = whiteColor;
			}
			else {
				pict[i3] = blackColor;
			}
		}
				
		byte[] pixels = new byte[pict.length];
		for (int i4 = 0; i4 < pict.length; i4++) {
			pixels[i4] = (byte) pict[i4];
		}
		ip.setPixels(pixels);
		iPlus = new ImagePlus("ip", ip);
		
		Roi selectionRoi = selectFromMask(iPlus);
		
		return selectionRoi;
    }
	
	//also from Object_Counter3D
	public boolean withinBounds(int m,int n,int o) {
        return (m >= 0 && m < Width && n >=0 && n < Height && o > 0 && o <= NbSlices );
    }
	
	//also from Object_Counter3D
	public int offset(int m,int n,int o) {
        return m+n*Width+(o-1)*Width*Height;
    }
	
	//also from Object_Counter3D
	public void replacetag(int m,int n){
        for (int i=0; i<tag.length; i++) if (tag[i]==m) tag[i]=n;
    }
	
		//method derived from https://imagej.nih.gov/ij/source/ij/plugin/Selection.java
	//All I've done is rewritten that method to be more convenient [doesn't need an ImageWindow object]
	
	public Roi selectFromMask(ImagePlus iPlus) {
		ImageProcessor ip = iPlus.getProcessor();
		
		if (!ip.isBinary()) {
			IJ.error("SelectionFromMask", "Image not recognised as binary image");
			return null;
		}
		

		int threshold = ip.isInvertedLut()?0:255;
		//only values equal to white will be selected.
		ip.setThreshold(threshold, threshold, ImageProcessor.NO_LUT_UPDATE);
		
		
		//IJ.runPlugIn("ij.plugin.filter.ThresholdToSelection", "");
		//Trying to simulate the exact same thing below, but:
		//my method here doesn't include the line:
		//'tts.setup(" ", WindowManager.getCurrentImage()) [paraphrased].
		//I want to be able to just pass in the ImagePlus to use.
		ThresholdToSelection tts = new ThresholdToSelection();
		tts.setup("", iPlus);
		
		
		prepareProcessor(ip, iPlus);
		
		tts.run(ip);

		Roi selectionRoi = iPlus.getRoi();
		return selectionRoi;
	}
	
	//I don't know how much of this method is actually required for ThresholdToSelection, but I'll use it all for now.
	//from https://github.com/imagej/imagej1/blob/master/ij/plugin/filter/PlugInFilterRunner.java
	private void prepareProcessor(ImageProcessor ip, ImagePlus imp) {
		ImageProcessor mask = imp.getMask();
		Roi roi = imp.getRoi();
		if (roi!=null && roi.isArea())
			ip.setRoi(roi);
		else
			ip.setRoi((Roi)null);
		if (imp.getStackSize()>1) {
			ImageProcessor ip2 = imp.getProcessor();
			double min1 = ip2.getMinThreshold();
			double max1 = ip2.getMaxThreshold();
			double min2 = ip.getMinThreshold();
			double max2 = ip.getMaxThreshold();
			if (min1!=ImageProcessor.NO_THRESHOLD && (min1!=min2||max1!=max2))
				ip.setThreshold(min1, max1, ImageProcessor.NO_LUT_UPDATE);
		}
		//float[] cTable = imp.getCalibration().getCTable();
		//ip.setCalibrationTable(cTable);
	}
	
		//method copied across from https://imagej.nih.gov/ij/developer/source/ij/plugin/RoiEnlarger.java.html
	public Roi enlargeRoi(Roi roi, int enlargeFactor) {
		roi = (Roi) roi.clone();
		int type = roi.getType();
        if (type==Roi.RECTANGLE || type==Roi.OVAL)
            return enlargeRectOrOval(roi, enlargeFactor);
        Rectangle bounds = roi.getBounds();
        int width = bounds.width;
        int height = bounds.height;
        width += 2*enlargeFactor +2;
        height += 2*enlargeFactor +2;
        ImageProcessor ip = new ByteProcessor(width, height);
        ip.invert();
        roi.setLocation(enlargeFactor+1, enlargeFactor+1);
        ip.setColor(0);
        ip.fill(roi);
        roi.setLocation(bounds.x, bounds.y);
        boolean bb = Prefs.blackBackground;
        Prefs.blackBackground = true;
        new EDM().toEDM(ip);
        //new ImagePlus("ip", ip).show();
        Prefs.blackBackground = bb;
        ip.setThreshold(0, enlargeFactor, ImageProcessor.NO_LUT_UPDATE);
        Roi roi2 = (new ThresholdToSelection()).convert(ip);
        if (roi2==null)
            return roi;
        roi2.setLocation(bounds.x-enlargeFactor, bounds.y-enlargeFactor);
        roi2.setStrokeColor(roi.getStrokeColor());
        if (roi.getStroke()!=null)
            roi2.setStroke(roi.getStroke());
        return roi2;
	}
	
	    
    private Roi enlargeRectOrOval(Roi roi, int n) {
        Rectangle bounds = roi.getBounds();
        bounds.x -= n;
        bounds.y -= n;
        bounds.width += 2*n;
        bounds.height += 2*n;
        if (bounds.width<=0 || bounds.height<=0)
            return roi;
        if (roi.getType()==Roi.RECTANGLE)
            return new Roi(bounds.x, bounds.y, bounds.width, bounds.height);
        else
            return new OvalRoi(bounds.x, bounds.y, bounds.width, bounds.height);
    }
	
	public void applyRoi(ImagePlus iPlus, Roi roi, Roi outerRoi) {
		if (outerRoi == null) {
			iPlus.setRoi(roi);
		}
		else {
			Roi andedRoi = andRoi(roi, outerRoi);
			iPlus.setRoi(andedRoi);
		}
			
	}
	
	//method 'and()' from https://imagej.nih.gov/ij/developer/source/ij/plugin/frame/RoiManager.java.html
	public ShapeRoi andRoi(Roi a, Roi b) {
		ShapeRoi s1 = new ShapeRoi(a);
		ShapeRoi s2 = new ShapeRoi(b);
		if (s1 == null || s2 == null) {
			System.err.println("andRoi failed, one of the rois is null");
		}
		s1.and(s2);
		return s1;
	}
	
}