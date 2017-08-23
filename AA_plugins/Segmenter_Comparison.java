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

public class Segmenter_Comparison implements PlugInFilter {

	ImagePlus image;
	public int X;
	public int Y;
	public int Z;


	public int upperSliceLimit;
	public int lowerSliceLimit;
	public boolean reverse;
	public Roi stackRoi;
	public Roi rootRoi;



	public Segmenter_Comparison() {
		System.err.println("Initialising log");
	}

	public int setup(String arg, ImagePlus imp) {
		this.image = imp;
		this.X = image.getWidth();
		this.Y = image.getHeight();
		this.Z = image.getStackSize();
		
		if (arg.equals("about")) {
			return DONE; //These enums are defined in PlugInFilter.
		}
		return DOES_16 + DOES_32 + DOES_8G + PARALLELIZE_STACKS + SUPPORTS_MASKING;
	}


	public void setSliceLimits(int limitA, int limitB, boolean reverseSlices) {
		if (Math.max(limitA, limitB) == limitB) {
			this.upperSliceLimit = limitB;
			this.lowerSliceLimit = limitA;
		}
		else {
			this.upperSliceLimit = limitA;
			this.lowerSliceLimit = limitB;
		}
		this.reverse = reverseSlices;
		if (this.reverse) {
			this.reverseStack();
		}
	}

	public void setStackRoi(Roi stackRoi) {
		this.stackRoi = stackRoi;
	}

	public void setRootRoi(Roi rootRoi) {
		this.rootRoi = rootRoi;
	}

	//copied and modified from https://imagej.nih.gov/ij/source/ij/plugin/StackReverser.java
	//https://imagej.nih.gov/ij/plugins/reverser.html
	public void reverseStack() {
		ImageStack stack = this.image.getStack();
		int n = stack.getSize();
		if (n == 1) return;
		ImageStack stack2 = new ImageStack(this.image.getWidth(), this.image.getHeight(), n);
		for (int i = 1; i <= n; i++) {
			stack2.setPixels(stack.getPixels(i), (n-i+1));
			stack2.setSliceLabel(stack.getSliceLabel(i),n-i+1);
		}
		this.image.setStack(stack2);

	}

	public void run(ImageProcessor ip) {
		new ConversionFrame(this);
	}
	


}

enum Method {
	GLOBAL_ITERATIVE, GLOBAL_THRESHOLD, COLOR_BLOBS
}

class BasicFrame extends JFrame {
	Segmenter_Comparison plugin;
	
	public BasicFrame(Segmenter_Comparison plugin) {
		this.plugin = plugin;
		this.setTitle("Segmentation");
		this.setSize(500,500);
		this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		this.setLayout(new FlowLayout());
	}
}

class ConversionFrame extends BasicFrame {

	JLabel methodSelectionInstruction;
	JRadioButton globalIterative;
	JRadioButton globalThreshold;
	JRadioButton colorBlobs;
	ButtonGroup methodSelection;
	JButton methodSelected;
	public Method method;

	JLabel topSliceLabel;
	JLabel bottomSliceLabel;
	JTextField topSliceNo;
	JTextField bottomSliceNo;
	JButton bothSelected;
	JCheckBox reverseSlices;
	boolean reverse;

	JLabel stackRoiInstruction;
	JButton stackRoiSelected;
	public Roi stackRoi;
	ImagePlus displayedSlice;

	JLabel rootSelectionInstruction;
	public Roi firstSliceRoi;
	JButton firstRootSelected;

	public ConversionFrame(Segmenter_Comparison plugin) {
		super(plugin);

		methodSelectionInstruction = new JLabel("Select which method to set-up");
		globalThreshold = new JRadioButton(Method.GLOBAL_THRESHOLD.toString(), false);
		globalIterative = new JRadioButton(Method.GLOBAL_ITERATIVE.toString(), true);
		colorBlobs = new JRadioButton(Method.COLOR_BLOBS.toString(), false);
		methodSelection = new ButtonGroup();
		methodSelection.add(globalIterative);
		methodSelection.add(globalThreshold);
		methodSelection.add(colorBlobs);
		methodSelected = new JButton("Press when happy with method selected");

		this.add(methodSelectionInstruction);
		JPanel methodOptions = new JPanel();
		methodOptions.setLayout(new BoxLayout(methodOptions, BoxLayout.Y_AXIS));
		methodOptions.add(globalThreshold);
		methodOptions.add(globalIterative);
		methodOptions.add(colorBlobs);
		this.add(methodOptions);
		this.add(methodSelected);


		methodSelected.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Method method;
				if(ConversionFrame.this.globalIterative.isSelected()) {
					method = Method.GLOBAL_ITERATIVE;
				}
				else if (ConversionFrame.this.globalThreshold.isSelected()) {
					method = Method.GLOBAL_THRESHOLD;
				}
				else {
					method = Method.COLOR_BLOBS;
				}
				ConversionFrame.this.setMethod(method);

			}
		});
		

		JPanel sliceSelectionPanel = new JPanel();
		sliceSelectionPanel.setLayout(new BoxLayout(sliceSelectionPanel, BoxLayout.Y_AXIS));

		topSliceLabel = new JLabel("Upper slice limit: ");
		bottomSliceLabel = new JLabel("Lower slice limit: ");
		topSliceNo = new JTextField(Integer.toString(plugin.image.getStackSize()), 5);
		bottomSliceNo = new JTextField("0", 5);
		bothSelected = new JButton("Press when happy with top and bottom selection");
		reverseSlices = new JCheckBox("Reverse slices?", false);

		JPanel topSliceSelectionPanel = new JPanel();
		topSliceSelectionPanel.setLayout(new BoxLayout(topSliceSelectionPanel, BoxLayout.X_AXIS));
		JPanel bottomSliceSelectionPanel = new JPanel();
		bottomSliceSelectionPanel.setLayout(new BoxLayout(bottomSliceSelectionPanel, BoxLayout.X_AXIS));

		topSliceSelectionPanel.add(topSliceLabel);
		bottomSliceSelectionPanel.add(bottomSliceLabel);
		topSliceSelectionPanel.add(topSliceNo);
		bottomSliceSelectionPanel.add(bottomSliceNo);
		sliceSelectionPanel.add(topSliceSelectionPanel);
		sliceSelectionPanel.add(bottomSliceSelectionPanel);

		sliceSelectionPanel.add(reverseSlices);
		sliceSelectionPanel.add(bothSelected);
		this.add(sliceSelectionPanel);

		bothSelected.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Integer top = Integer.parseInt(ConversionFrame.this.topSliceNo.getText());
				Integer bottom = Integer.parseInt(ConversionFrame.this.bottomSliceNo.getText());
				Integer maxSize = ConversionFrame.this.plugin.image.getStackSize();
				if (top == null || bottom == null) {
					JOptionPane.showMessageDialog(null, "Both top slice and bottom slice numbers must be selected");
					return;
				}
				if (top > maxSize || bottom > maxSize) {
					JOptionPane.showMessageDialog(null, "Both top and bottom slice numbers must be less than max - (" + maxSize + ")");
					return;
				}
				Boolean reverse = ConversionFrame.this.reverseSlices.isSelected();
				ConversionFrame.this.plugin.setSliceLimits(top, bottom, reverse);
				ConversionFrame.this.reverse = reverse;

				ConversionFrame.this.disableSliceSelection();
				ConversionFrame.this.enableStackRoiSelection();
			}
		});

		stackRoiInstruction = new JLabel("Please select an ROI to apply to the entire image stack");
		stackRoiSelected = new JButton("ROI selected");

		this.add(stackRoiInstruction);
		this.add(stackRoiSelected);

		stackRoiSelected.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Roi stackRoi = ConversionFrame.this.displayedSlice.getRoi();
				if (stackRoi == null) {
					JOptionPane.showMessageDialog(null, "Please select an roi, then click " + ConversionFrame.this.stackRoiSelected.getText());
					return;
				}
				ConversionFrame.this.setStackRoi(stackRoi);

				ConversionFrame.this.disableStackRoiSelection();
				ConversionFrame.this.enableRootSelection();
			}

		});

		firstRootSelected = new JButton("Initial Roots selected");
		rootSelectionInstruction = new JLabel("Please select all the roots on the initial slice");
		this.add(rootSelectionInstruction);
		this.add(firstRootSelected);

		firstRootSelected.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Roi rootRoi = ConversionFrame.this.displayedSlice.getRoi();
				if (rootRoi == null) {
					JOptionPane.showMessageDialog(null, "Please select all the roots on the initial slice");
					return;
				}
				ConversionFrame.this.plugin.setRootRoi(rootRoi);
				//ConversionFrame.this.disableRootSelection();

			}
		});

		//TODO do stuff with adding to queues.

		this.disableSliceSelection();
		this.disableStackRoiSelection();
		this.disableRootSelection();
		this.setVisible(true);
	}

	public void setMethod(Method method) {
		this.method = method;
		if (this.method == Method.GLOBAL_ITERATIVE) {
			System.out.println("Selected GLobal_Iterative method!");
			this.disableMethodSelection();
			this.enableSliceSelection();
		}
		else if (this.method == Method.GLOBAL_THRESHOLD) {
			//TODO run that method somehow.
			System.out.println("Selected Global_Threshold method!");
			ConversionFrame.this.dispatchEvent(new WindowEvent(ConversionFrame.this, WindowEvent.WINDOW_CLOSING));
		}
		else {
			//this.method == Method.COLOR_BLOBS
			//run the Color_Blobs method.
			System.out.println("Selected Color_Blobs method!");
			ConversionFrame.this.dispatchEvent(new WindowEvent(ConversionFrame.this, WindowEvent.WINDOW_CLOSING));

		}
	}

	public void setStackRoi(Roi roi) {
		this.plugin.setStackRoi(roi);
		this.displayedSlice.close();
	}

	public void disableMethodSelection() {

		this.methodSelectionInstruction.setEnabled(false);
		this.globalThreshold.setEnabled(false);
		this.globalIterative.setEnabled(false);
		this.colorBlobs.setEnabled(false);
		this.methodSelected.setEnabled(false);
	}


	public void enableSliceSelection() {
		this.topSliceLabel.setEnabled(true);
		this.bottomSliceLabel.setEnabled(true);
		this.topSliceNo.setEnabled(true);
		this.bottomSliceNo.setEnabled(true);
		this.bothSelected.setEnabled(true);
		this.reverseSlices.setEnabled(true);
	}

	public void disableSliceSelection() {
		this.topSliceLabel.setEnabled(false);
		this.bottomSliceLabel.setEnabled(false);
		this.topSliceNo.setEnabled(false);
		this.bottomSliceNo.setEnabled(false);
		this.bothSelected.setEnabled(false);
		this.reverseSlices.setEnabled(false);
	}

	public void enableStackRoiSelection() {
		ImageProcessor ip_one = this.plugin.image.getStack().getProcessor(1);
		displayedSlice = new ImagePlus("First Image - please select stack roi", ip_one);
		displayedSlice.show();
		this.stackRoiSelected.setEnabled(true);
		this.stackRoiInstruction.setEnabled(true);
	}

	public void disableStackRoiSelection() {
		this.stackRoiSelected.setEnabled(false);
		this.stackRoiInstruction.setEnabled(false);
	}

	public void enableRootSelection() {
		this.firstRootSelected.setEnabled(true);
		this.rootSelectionInstruction.setEnabled(true);
		ImageProcessor firstSlice = this.plugin.image.getStack().getProcessor(1);
		displayedSlice = new ImagePlus("First Image - please select initial root area.", firstSlice);
		displayedSlice.show();
	}

	public void disableRootSelection() {
		this.firstRootSelected.setEnabled(false);
		this.rootSelectionInstruction.setEnabled(false);
	}
}


