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
	
	//Called by the system when the plugin is run. [arg is potentially selected by the user at that time]
	public int setup(String arg, ImagePlus imp) {
		this.image = imp;
		System.out.println("Stack size: " + image.getStackSize());

		return DOES_16 + DOES_32 + DOES_8G + PARALLELIZE_STACKS + SUPPORTS_MASKING;
	}
	
	public void run(ImageProcessor ip) {
		System.out.println(ip.getBitDepth());
		new SelectTopBottom(this);

		
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
				stack.deleteSlice(1);
			}
			for (int i = 0; i < bottomSliceRelativeToBottomStack; i++) {
				//delete the last slice the correct number of times (each iteration changes stack.getSize())
				stack.deleteSlice(stack.getSize());
			}
			ImagePlus newImage = new ImagePlus("aa", stack);
			newImage.show();
			this.image.changes = false;
			this.image.close();
			this.image = newImage;
		}
		else {
			return "ERROR: Conversion expects a virtual stack";
		}
		return Conversion_.success;
	}
	
	//called by the bothSelected button's actionListener in SelectTopBottom... hmmm might want to try and make this control flow a little more sensible.
	public void selectCircles() {
		
		this.image.hide();
		new SelectCircles(this);
		//this.adjustBrightnessContrast(); to be called after this...
	}
	
	public void completedCircleSelection(Roi topEllipse, Roi bottomEllipse) {
		System.out.println("here");
		this.image.setRoi(topEllipse);
		System.out.println("DUPLICATING INTO RAM BEGIN: This will take a while, no loading bar yet...");
		image = this.image.duplicate();
		image.show();
		topEllipse.setLocation(0, 0);
		image.setRoi(topEllipse);
		
		adjustBrightnessContrast();
	}
	
	public void adjustBrightnessContrast() {
		
		System.out.println(this.image.getWidth()*this.image.getHeight()*this.image.getStack().getSize());
		//StackStatistics stats = new StackStatistics(this.image);
		//System.out.println(stats);
		
		//System.out.println("Area: " + stats.area + ", mode " + stats.mode + ", dmode " + stats.dmode + ", stdDev " + stats.stdDev);
		
		
	}
}

class BasicFrame extends JFrame {
	Conversion_ plugin;
	
	public BasicFrame(Conversion_ plugin) {
		this.plugin = plugin;
		this.setTitle("Segmentation");
		this.setSize(500,500);
		this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		this.setLayout(new FlowLayout());
	}
}

class SelectTopBottom extends BasicFrame {
	JButton topSelect;
	JButton bottomSelect;
	
	JLabel topSliceLabel;
	JLabel bottomSliceLabel;
	
	JButton bothSelected;
	public SelectTopBottom(Conversion_ plugin) {
		
		super(plugin);
		
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
					SelectTopBottom.this.plugin.selectCircles();
					
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
		
		this.add(topSelect);
		this.add(topSliceLabel);
		this.add(bottomSelect);
		this.add(bottomSliceLabel);
		
		this.add(bothSelected);
		
		this.setVisible(true);
	}
}

class SelectCircles extends BasicFrame {
	
	JButton selectTopRoi;
	JButton selectBottomRoi;
	
	ImagePlus displayedSlice;
	public Roi topEllipse;
	public Roi bottomEllipse;
	ImageStack stack;
	
	public SelectCircles(Conversion_ plugin) {
		super(plugin);
		//display the top image.
		stack = this.plugin.image.getStack();
		
		
		selectTopRoi = new JButton("Selected top ellipse");
		selectTopRoi.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				SelectCircles.this.topEllipse = displayedSlice.getRoi();
				if (SelectCircles.this.topEllipse == null) {
					JOptionPane.showMessageDialog(null, "Please select an roi, then click 'Selected top ellipse'");
				}
				else {
				SelectCircles.this.selectBottomRoi.setEnabled(true);
				SelectCircles.this.getLastEllipse();
				}
			}
		});
		
		selectBottomRoi = new JButton("Selected bottom ellipse");
		selectBottomRoi.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				SelectCircles.this.bottomEllipse = displayedSlice.getRoi();
				if (SelectCircles.this.bottomEllipse == null) {
					JOptionPane.showMessageDialog(null, "Please select an roi, then click 'Selected bottom ellipse'");
				}
			}
		});
		
		this.add(selectTopRoi);
		this.add(selectBottomRoi);
		this.selectBottomRoi.setEnabled(false);
		this.setVisible(true);
		

		getFirstEllipse();
		
	}
	
	void getFirstEllipse() {
		ImageProcessor ip_one = stack.getProcessor(1);
		displayedSlice = new ImagePlus("First Image - please select roi", ip_one);
		displayedSlice.show();
	}
	
	void getLastEllipse() {
		ImageProcessor ip_last = stack.getProcessor(stack.getSize());
		displayedSlice.changes = false;
		displayedSlice.close();
		
		displayedSlice = new ImagePlus("Last image - please select roi", ip_last);
		displayedSlice.setRoi(topEllipse);
		displayedSlice.show();
		switch (JOptionPane.showConfirmDialog(null, "Currently the top ellipse is overlayed - is this roi ok?")) {
			case JOptionPane.YES_OPTION:
				System.out.println("Same ellipse is ok!");
				bottomEllipse = topEllipse;
				break;
			case JOptionPane.NO_OPTION:
				System.err.println("Selecting a different ellipse is not implemented yet.");
				bottomEllipse = topEllipse;
				break;
			case JOptionPane.CANCEL_OPTION:
				System.err.println("Cancel was pressed. The same ellipse option will be used.");
				bottomEllipse = topEllipse;
				break;
			default:
				System.err.println("in getLastEllipse neither JOptionPaneOption was selected.");
				break;
		}
		displayedSlice.changes = false;
		displayedSlice.close();
		this.plugin.completedCircleSelection(this.topEllipse, this.bottomEllipse);
		
	}
}

