package AA_plugins;

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
import externalPluginCopies.*;
import externalPluginCopies.FilterPlugin.FilterType;
import javax.swing.*;
import java.awt.event.*;

public class Conversion_ implements PlugInFilter  {
	
	ImagePlus image;
	Integer topSliceNumber;
	Integer bottomSliceNumber;
	public static String success = "Success!";
	public Conversion_() {
		System.err.println("Initialising log");
	}
	
	public void run(ImageProcessor ip) {
		System.out.println(ip.getBitDepth());
		SelectTopBottom frame = new SelectTopBottom(this);
	}
	
		//Called by the system when the plugin is run. [arg is selected by the user at that time]
	public int setup(String arg, ImagePlus imp) {
		this.image = imp;
		System.out.println("Stack size: " + image.getStackSize());

		return DOES_16 + DOES_32 + DOES_8G + PARALLELIZE_STACKS + SUPPORTS_MASKING;
	}
	
	public void showAbout() {
		IJ.showMessage("About Segmentation_...", "Attempt 1 -- method copied as closely as possible from Laura and Dan's.");
	}
	
	public Integer setTopSlice() {
		this.topSliceNumber = image.getCurrentSlice();
		System.out.println("new top slice is " + this.topSliceNumber);
		return this.topSliceNumber;
	}
	
	public Integer setBottomSlice() {
		this.bottomSliceNumber = image.getCurrentSlice();
		System.out.println("new bottom slice is " + this.bottomSliceNumber);
		return this.bottomSliceNumber;
	}
	
	public Integer getTopSliceNumber() {
		return this.topSliceNumber;
	}
	
	public Integer getBottomSliceNumber() {
		return this.bottomSliceNumber;
	}
	
	//Returns an error message if unsuccesful.
	public String cropImage() {
		
		if (topSliceNumber == null || bottomSliceNumber == null) {
			return "both the top and the bottom of the stack must be defined";
		}
		if (topSliceNumber > bottomSliceNumber) {
			return "topSliceNumber must be lower than bottomSliceNumber";
		}
		
		ImageStack stack = this.image.getStack();
		int bottomSliceRelativeToBottomStack = stack.getSize() - bottomSliceNumber;
		if (stack.isVirtual()) {

			for (int i = 1; i < topSliceNumber; i++) {
				stack.deleteSlice(i);
			}
			for (int i = 0; i < bottomSliceRelativeToBottomStack; i++) {
				//delete the last slice the correct number of times (each iteration changes stack.getSize())
				stack.deleteSlice(stack.getSize());
			}
			ImagePlus newImage = new ImagePlus("aa", stack);
			newImage.show();
			this.image.changes = false;
			this.image.close();
		}
		else {
			return "ERROR: Conversion expects a virtual stack";
		}
		return Conversion_.success;

	}
}



class SelectTopBottom extends JFrame {
	JButton topSelect;
	JButton bottomSelect;
	
	JLabel topSliceLabel;
	JLabel bottomSliceLabel;
	
	JButton bothSelected;
	Conversion_ plugin;
	public SelectTopBottom(Conversion_ plugin) {
		
		this.plugin = plugin;
		this.setTitle("Segmentation");
		this.setSize(500,500);
		this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		
		topSelect = new JButton("Press when the top slice is selected");
		bottomSelect = new JButton("Press when the bottom slice is selected");
		
		topSliceLabel = new JLabel("Current top slice number: ");
		bottomSliceLabel = new JLabel("Current bottom slice number: ");
		
		bothSelected = new JButton("Press when happy with top and bottom selection");
		bothSelected.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {

				String errorMessage = SelectTopBottom.this.plugin.cropImage();
				if (errorMessage == Conversion_.success) {
					System.out.println("SelectTopBottom - " + Conversion_.success);
					//Close the SelectTopBottom window
					SelectTopBottom.this.dispatchEvent(new WindowEvent(SelectTopBottom.this, WindowEvent.WINDOW_CLOSING));
					
				}
				else {
					//display an error message
					System.out.println(errorMessage);
				}
			}
		});
				
		topSelect.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int topSliceNumber = SelectTopBottom.this.plugin.setTopSlice();
				SelectTopBottom.this.topSliceLabel.setText("Current top slice number: " + topSliceNumber);
			}
		});
		bottomSelect.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int bottomSliceNumber = SelectTopBottom.this.plugin.setBottomSlice();
				SelectTopBottom.this.bottomSliceLabel.setText("Current bottom slice number: " + bottomSliceNumber);
			}
		});
		
		this.setLayout(new FlowLayout());
		this.add(topSelect);
		this.add(topSliceLabel);
		this.add(bottomSelect);
		this.add(bottomSliceLabel);
		
		this.add(bothSelected);
		
		this.setVisible(true);
	}
}

