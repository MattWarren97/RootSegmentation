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


// from https://github.com/fiji/Fiji_Plugins/blob/master/src/main/java/fiji/process3d/EDT.java
//I did spend about 1.5 hours trying to work out how to import that file.
//I failed, hence just copied the code to here. [the EDTExtra class below that extends this is used for the transform]
public class EDT implements PlugInFilter {
	ImagePlus image;
	int w, h, d;
	int current, total;
	static int callCount = 0;
	
	public ImagePlus performTransform(ImagePlus imp) {
		if (imp == null) {
			System.err.println("imp was null");
		}
		ImagePlus iPlus = compute(imp.getStack());
		return iPlus;
	}
	
	public int setup(String arg, ImagePlus image) {
		this.image = image;
		return DOES_8G | NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		callCount++;
		compute(image.getStack()).show();
		System.out.println("Call count is now " + callCount);
	}

	public ImagePlus compute(ImageStack stack) {
		w = stack.getWidth();
		h = stack.getHeight();
		d = stack.getSize();
		ImageStack result = new ImageStack(w, h, d);
		for (int i = 1; i <= d; i++)
			result.setPixels(new float[w * h], i); //set slice i to the (float) pixel array.

		current = 0;
		total = w * h * d * 3;

		new Z(stack, result).compute();
		new Y(result).compute();
		new X(result).compute();

		return new ImagePlus("EDT", result);
	}

	abstract class EDTBase {
		int width;
		/*
		 * parabola k is defined by y[k] (v in the paper)
		 * and f[k] (f(v[k]) in the paper): (y, f) is the
		 * coordinate of the minimum of the parabola.
		 * z[k] determines the left bound of the interval
		 * in which the k-th parabola determines the lower
		 * envelope.
		 */

		int k;
		float[] f, z;
		int[] y;

		EDTBase(int rowWidth) {
			width = rowWidth;
			f = new float[width + 1];
			z = new float[width + 1];
			y = new int[width + 1];
		}

		final void computeRow() {
			// calculate the parabolae ("lower envelope")
			f[0] = Float.MAX_VALUE;
			y[0] = -1;
			z[0] = Float.MAX_VALUE;
			k = 0;
			float fx, s;
			for (int x = 0; x < width; x++) {
				fx = get(x);
				for (;;) {
					// calculate the intersection
					s = ((fx + x * x) - (f[k] + y[k] * y[k])) / 2 / (x - y[k]);
					if (s > z[k])
						break;
					if (--k < 0)
						break;
				}
				k++;
				y[k] = x;
				f[k] = fx;
				z[k] = s;
			}
			z[++k] = Float.MAX_VALUE;
			// calculate g(x)
			int i = 0;
			for (int x = 0; x < width; x++) {
				while (z[i + 1] < x)
					i++;
				set(x, (x - y[i]) * (x - y[i]) + f[i]);
			}
		}

		abstract float get(int column);

		abstract void set(int column, float value);

		final void compute() {
			while (nextRow()) {
				computeRow();
				if (total > 0) {
					current += width;
					//IJ.showProgress(current, total);
				}
			}
		}

		abstract boolean nextRow();
	}

	class Z extends EDTBase {
		byte[][] inSlice;
		float[][] outSlice;
		int offset;

		Z(ImageStack in, ImageStack out) {
			super(d);
			inSlice = new byte[d][];
			outSlice = new float[d][];
			for (int i = 0; i < d; i++) {
				inSlice[i] = (byte[])in.getPixels(i + 1);
				outSlice[i] = (float[])out.getPixels(i + 1);
			}
			offset = -1;
		}

		final float get(int x) {
			return inSlice[x][offset] == 0 ? 0 : Float.MAX_VALUE;
		}

		final void set(int x, float value) {
			outSlice[x][offset] = value;
		}

		final boolean nextRow() {
			return ++offset < w * h;
		}
	}

	abstract class OneDimension extends EDTBase {
		ImageStack stack;
		float[] slice;
		int offset, lastOffset, rowStride, columnStride, sliceIndex;

		OneDimension(ImageStack out, boolean iterateX) {
			super(iterateX ? w : h);
			stack = out;
			columnStride = iterateX ? 1 : w;
			rowStride = iterateX ? w : 1;
			offset = w * h;
			lastOffset = rowStride * (iterateX ? h : w);
			sliceIndex = -1;
		}

		final float get(int x) {
			return slice[x * columnStride + offset];
		}

		final boolean nextRow() {
			offset += rowStride;
			if (offset >= lastOffset) {
				if (++sliceIndex >= d)
					return false;
				offset = 0;
				slice = (float[])stack.getPixels(sliceIndex + 1);
			}
			return true;
		}
	}

	class Y extends OneDimension {
		Y(ImageStack out) {
			super(out, false);
		}

		final void set(int x, float value) {
			slice[x * columnStride + offset] = value;
		}
	}

	class X extends OneDimension {
		X(ImageStack out) {
			super(out, true);
		}

		final void set(int x, float value) {
			slice[x * columnStride + offset] = (float)Math.sqrt(value);
		}
	}
}