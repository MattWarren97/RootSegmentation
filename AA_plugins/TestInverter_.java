import ij.*; //   \\Cseg_2\erc\ADMIN\Programmes\Fiji_201610_JAVA8.app\jars\ij-1.51g.jar needs to be added to classpath when compiling(-cp))
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import java.awt.*;


public class TestInverter_ implements PlugInFilter {

	public void run(ImageProcessor ip) {
		byte[] pixels = (byte[])ip.getPixels();
		int width = ip.getWidth();
		Rectangle r = ip.getRoi();
		
		int offset, i;
		
		for (int y = r.y; y<(r.y+r.height); y++) {
			offset = y*width;
			for (int x = r.x; x<(r.x+r.width); x++) {
				i = offset+x;
				pixels[i] = (byte) (255-pixels[i]);
			}
		}
		
	}
	
	void showAbout() {
		IJ.showMessage("About TestInverter_...", "This test plugin takes an 8-bit (greyscale) image and inverts it.");
	}
	
	//Called by the system when the plugin is run. [arg is selected by the user at that time]
	public int setup(String arg, ImagePlus imp) {
		if (arg.equals("about")) {
			showAbout();
			return DONE; //These enums are defined in PlugInFilter.
		}
		return DOES_8G+DOES_STACKS+SUPPORTS_MASKING;
	}
}


