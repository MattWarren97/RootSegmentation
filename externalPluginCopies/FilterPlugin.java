package externalPluginCopies;

import ij.*; //   \\Cseg_2\erc\ADMIN\Programmes\Fiji_201610_JAVA8.app\jars\ij-1.51g.jar needs to be added to classpath when compiling(-cp))
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import java.awt.*;
import java.awt.image.*;
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




public class FilterPlugin {
	
	
	public enum FilterType {
		MEDIAN,
		MIN
	}
	
	public FilterPlugin() {

	}
		
	
		//this implementation from http://svg.dmi.unict.it/iplab/imagej/Plugins/Forensics/Median_filter2/Median_Filter.html
	public void applyFilter(ImageProcessor ip, int radius, FilterType filter) {
		
		Rectangle r = ip.getRoi();
		int width = (int) r.getWidth();
		int height = (int) r.getHeight();
		
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
	
	public void erode3d(ImagePlus image) {
		ImagePlus result = new Erode().erode(image, 255);
		image.setStack(result.getStack());
	}
	
	public void dilate3d(ImagePlus image) {
		ImagePlus result = new Dilate().dilate(image, 255);
		image.setStack(result.getStack());
	}
}

//copied and modified from org.doube.bonej.
//http://www.javased.com/index.php?source_dir=BoneJ/src/org/doube/bonej/Erode.java
class Erode {

	private int w, h, d;
	private byte[][] pixels_in;
	private byte[][] pixels_out;
	
	//threshold is the minimum value that will be 'eroded' .. I think.
	public ImagePlus erode(ImagePlus image, int threshold) {
	
		// Determine dimensions of the image
		w = image.getWidth();
		h = image.getHeight();
		d = image.getStackSize();

		this.pixels_in = new byte[d][];
		this.pixels_out = new byte[d][];
		for (int z = 0; z < d; z++) {
			this.pixels_in[z] = (byte[]) image.getStack().getPixels(z + 1);
			this.pixels_out[z] = new byte[w * h];
		}
		
		// iterate
		for (int z = 0; z < d; z++) {
		
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					if (get(x, y, z) != threshold)
						set(x, y, z, get(x, y, z));
					else if (get(x - 1, y, z) == threshold
							&& get(x + 1, y, z) == threshold
							&& get(x, y - 1, z) == threshold
							&& get(x, y + 1, z) == threshold
							&& get(x, y, z - 1) == threshold
							&& get(x, y, z + 1) == threshold)

						set(x, y, z, threshold);
					else
						set(x, y, z, 0);
					
				}
			}
		}

		ColorModel cm = image.getStack().getColorModel();

		// create output image
		ImageStack stack = new ImageStack(w, h);
		for (int z = 0; z < d; z++) {
			stack.addSlice(image.getImageStack().getSliceLabel(z + 1),
			new ByteProcessor(w, h, this.pixels_out[z], cm));
		}
		ImagePlus imp = new ImagePlus();
		imp.setCalibration(image.getCalibration());
		imp.setStack(null, stack);
		return imp;
	}

	public int get(int x, int y, int z) {
		x = x < 0 ? 0 : x;
		x = x >= w ? w - 1 : x;
		y = y < 0 ? 0 : y;
		y = y >= h ? h - 1 : y;
		z = z < 0 ? 0 : z;
		z = z >= d ? d - 1 : z;
		return (int) (this.pixels_in[z][y * w + x] & 0xff);
	}

	public void set(int x, int y, int z, int v) {
		this.pixels_out[z][y * w + x] = (byte) v;
		return;
	}
}


//http://www.javased.com/index.php?source_dir=BoneJ/src/org/doube/bonej/Dilate.java
class Dilate {

	private int w, h, d;
	private byte[][] pixels_in;
	private byte[][] pixels_out;

	public ImagePlus dilate(ImagePlus image, int threshold) {
		
		// Determine dimensions of the image
		w = image.getWidth();
		h = image.getHeight();
		d = image.getStackSize();

		this.pixels_in = new byte[d][];
		this.pixels_out = new byte[d][];
		for (int z = 0; z < d; z++) {
			this.pixels_in[z] = (byte[]) image.getStack().getPixels(z + 1);
			this.pixels_out[z] = new byte[w * h];
		}

		// iterate
		for (int z = 0; z < d; z++) {
			IJ.showProgress(z, d - 1);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					if (get(x, y, z) == threshold
							   || get(x - 1, y, z) == threshold
							   || get(x + 1, y, z) == threshold
							   || get(x, y - 1, z) == threshold
							   || get(x, y + 1, z) == threshold
							   || get(x, y, z - 1) == threshold
							   || get(x, y, z + 1) == threshold)

						set(x, y, z, threshold);
					else
						set(x, y, z, get(x, y, z));
				}
			}
		}

		ColorModel cm = image.getStack().getColorModel();

		// create output image
		ImageStack stack = new ImageStack(w, h);
		for (int z = 0; z < d; z++) {
			stack.addSlice(image.getImageStack().getSliceLabel(z + 1),
			new ByteProcessor(w, h, this.pixels_out[z], cm));
		}
		ImagePlus imp = new ImagePlus();
		imp.setCalibration(image.getCalibration());
		imp.setStack(null, stack);
		return imp;
	}

	public int get(int x, int y, int z) {
		x = x < 0 ? 0 : x;
		x = x >= w ? w - 1 : x;
		y = y < 0 ? 0 : y;
		y = y >= h ? h - 1 : y;
		z = z < 0 ? 0 : z;
		z = z >= d ? d - 1 : z;
		return (int) (this.pixels_in[z][y * w + x] & 0xff);
	}

	public void set(int x, int y, int z, int v) {
		this.pixels_out[z][y * w + x] = (byte) v;
		return;
	}
}
	