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



public class FilterPlugin {
	
	public enum FilterType {
		MEDIAN,
		MIN
	}
	
		//this implementation from http://svg.dmi.unict.it/iplab/imagej/Plugins/Forensics/Median_filter2/Median_Filter.html
	public void applyFilter(ImageProcessor ip, int radius, FilterType filter) {

		int width = X;
		int height = Y;
		
		byte[] pixels = (byte[]) ip.getPixels();
		
		int[] tmp=new int[pixels.length];
		for (int i=0;i<pixels.length;i++)
			tmp[i]=pixels[i]&0xff; //this.... is just pixels[i], surely?
		
		int[][] arrays = create2DIntArray(pixels, width, height);
		
		int [][] filteredArray = new int [width][height];
		for(int j=0;j<height;j++) {
			for(int i=0;i<width;i++) {
				if (filter == FilterType.MEDIAN) {
					filteredArray[i][j] = pixelMedian(arrays,radius,width,height,i,j);
				}
				else if (filter == FilterType.MIN) {
					filteredArray[i][j] = pixelMin(arrays,radius,width,height,i,j);
				}
				else {
					System.err.println("No filter applied - invalid filter type " + filter);
				}
			}
		}
		
		int[] output = array_2d_to_1d(filteredArray, width, height);
			
		for(int j=0;j<output.length;j++)
			pixels[j]=(byte)output[j];
	}
	
	public int pixelMedian(int[][] array2d, int radius, int width, int height, int x, int y) {
		
        int sum = 0;
        int countInRange = 0;
		int[] inRadius=new int[radius*radius];;
        for(int j=0;j<radius;j++)
        {
            for(int i=0;i<radius;i++)
            {
	            if(((x-1+i)>=0) && ((y-1+j)>=0) && ((x-1+i)<width) && ((y-1+j)<height))
                {
					inRadius[countInRange]=array2d[x-1+i][y-1+j];
	                countInRange++;
	            }
            }
        }
		Arrays.sort(inRadius);
        if(countInRange==0) 
            return 0;
		int medianIndex = (int)(countInRange/2);
        return (inRadius[medianIndex]);
    }
	
	public int pixelMin(int[][] array2d, int radius, int width, int height, int x, int y) {
		int min = 255;
		for (int j = 0; j< radius; j++) {
			for (int i = 0; i < radius; i++) {
	            if(((x-1+i)>=0) && ((y-1+j)>=0) && ((x-1+i)<width) && ((y-1+j)<height)) {
					int value = array2d[x-1+i][y-1+j];
					if (value < min) {
						min = value;
					}
				}
			}
		}

		return min;
	}
	
	private int[][] create2DIntArray(byte[] pix, int width, int height) {
		int[][] array2d = new int[width][height];
		
		for (int i = 0; i<width; i++) {
			for(int j = 0; j < height; j++) {
				array2d[i][j] = pix[i+(j*width)];
			}
		}
		return array2d;
	}
	
	private int[] array_2d_to_1d(int[][] values, int width, int height) {
		int[] output = new int [width*height];
		
		for(int i=0;i<width;i++) {
			for(int j=0;j<height;j++) {
				output[i+(j*width)] = values[i][j];
			}
		}
		
		return output;
	}
	
	
}