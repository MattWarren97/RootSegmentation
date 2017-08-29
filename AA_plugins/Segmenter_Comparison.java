package AA_plugins;

import ij.*; //   \\Cseg_2\erc\ADMIN\Programmes\Fiji_201610_JAVA8.app\jars\ij-1.51g.jar needs to be added to classpath when compiling(-cp))
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import java.awt.*;
import java.util.*;
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

	public ArrayList<SegmentationPlugin> toBeRun;
	SegmentationListFrame listFrame;


	public Segmenter_Comparison() {
		System.err.println("Initialising log");
		this.toBeRun = new ArrayList<SegmentationPlugin>();
		this.listFrame = new SegmentationListFrame(toBeRun);
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

	public void addPlugin(SegmentationPlugin plugin) {
		this.toBeRun.add(plugin);
		this.listFrame.updateTable(plugin);
		System.out.println("added a new plugin " + plugin);
		new ConversionFrame(this);
	}

	public void addGlobalThreshold() {
		Runnable r = new Runnable() {
			public void run() {
				ImagePlus iPlus = cropImage(Segmenter_Comparison.this.lowerSliceLimit, Segmenter_Comparison.this.upperSliceLimit);
				adjustBrightnessContrast(iPlus);

				Global_Threshold gt = new Global_Threshold(iPlus);
				Segmenter_Comparison.this.addPlugin(gt);
			}
		};
		Thread t = new Thread(r);
		t.start();
	}
	
	
	public void addColorBlobs() {
		Runnable r = new Runnable() {
			public void run() {
				ImagePlus iPlus = cropImage(Segmenter_Comparison.this.lowerSliceLimit, Segmenter_Comparison.this.upperSliceLimit);
				adjustBrightnessContrast(iPlus);

				Segmenter_Comparison.this.getOptions(Method.COLOR_BLOBS);
				//Color_Segmenter cs = new Color_Segmenter(iPlus, )
				//Segmenter_Comparison.this.addPlugin(cs);
			}
		};
		Thread t = new Thread(r);
		t.start();
	}
	
	public void addCS(ColorBlobOptions options) {
		Color_Segmenter cs = new Color_Segmenter(this.image, options);
		this.addPlugin(cs);
	}
		

	public void addGlobalIterative() {
		Runnable r = new Runnable() {
			public void run() {
				ImagePlus iPlus = cropImage(Segmenter_Comparison.this.lowerSliceLimit, Segmenter_Comparison.this.upperSliceLimit);
				adjustBrightnessContrast(iPlus);

				System.out.println("Should be a Global_Iterative options menu now!");
				Segmenter_Comparison.this.getOptions(Method.GLOBAL_ITERATIVE);
				//Segmenter_Comparison.this.addPlugin(new Global_Iterative());
			}
		};
		Thread t = new Thread(r);
		t.start();

	}
	
	public void addGI(float stdDev, int EDT_Threshold) {
		Global_Iterative gi = new Global_Iterative(this.image, this.stackRoi, this.rootRoi, stdDev, EDT_Threshold);
		this.addPlugin(gi);
	}

	public void adjustBrightnessContrast(ImagePlus image) {
		
		//System.out.println(image.getWidth()*image.getHeight()*image.getStack().getSize());
		StackStatistics stats = new StackStatistics(image);
		//System.out.println(stats);
		
		//System.out.println("Area: " + stats.area + ", mode " + stats.mode + ", dmode " + stats.dmode + ", stdDev " + stats.stdDev);
		System.out.println("Mode: " + stats.dmode + ", Mode - 3*SD: " + (stats.dmode - 3*stats.stdDev));
		double min = stats.dmode-3*stats.stdDev;
		double max = stats.dmode;
		image.setDisplayRange(min, max);
		image.show();
		System.out.println("finished duplicating - now converting to 8 bit ");
		ImageConverter ic = new ImageConverter(image);
		ic.convertToGray8();
		
	}

	public ImagePlus cropImage(int lowerLimit, int higherLimit) {
		ImageStack stack = this.image.getStack().duplicate();
		int higherLimitRelativeToStackEnd = stack.getSize() - higherLimit;
		for (int i = 1; i < lowerLimit; i++) {
			stack.deleteSlice(1);
		}
		for (int i = 0; i < higherLimitRelativeToStackEnd; i++) {
			stack.deleteSlice(stack.getSize());
		}

		return new ImagePlus("Global_Iterative", stack);
	}


	public void getOptions(Method method) {
		if (method == Method.COLOR_BLOBS) {
			new ColorBlobOptionsFrame(this);
		}

		else if (method == Method.GLOBAL_ITERATIVE) {
			new GlobalIterativeOptionsFrame(this);
		}

		else if (method == Method.GLOBAL_THRESHOLD) {

		}
	}


}

enum Method {
	GLOBAL_ITERATIVE, GLOBAL_THRESHOLD, COLOR_BLOBS
}

class BasicFrame extends JFrame {
	
	
	public BasicFrame() {
		this.setTitle("Segmentation");
		this.setSize(500,500);
		this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		this.setLayout(new FlowLayout());
	}
}

class ConversionFrame extends BasicFrame {

	Segmenter_Comparison plugin;
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
		super();
		this.plugin = plugin;

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
				ConversionFrame.this.setSliceLimits(top, bottom, reverse);
				//ConversionFrame.this.reverse = reverse;

				//ConversionFrame.this.disableSliceSelection();
				//ConversionFrame.this.enableStackRoiSelection();
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
				ConversionFrame.this.setRootRoi(rootRoi);
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
			System.out.println("Selected Global_Iterative method!");
			this.disableMethodSelection();
			this.enableSliceSelection();
		}
		else if (this.method == Method.GLOBAL_THRESHOLD) {
			System.out.println("Selected Global_Threshold method!");
			this.disableMethodSelection();
			this.enableSliceSelection();
		}
		else {
			//this.method == Method.COLOR_BLOB
			System.out.println("Selected Color_Blobs method!");
			this.disableMethodSelection();
			this.enableSliceSelection();

		}
	}

	public void setSliceLimits(int top, int bottom, boolean reverse) {
		this.plugin.setSliceLimits(top, bottom, reverse);
		this.reverse = reverse;

		this.disableSliceSelection();
		this.enableStackRoiSelection();

	}

	public void setStackRoi(Roi roi) {
		this.plugin.setStackRoi(roi);
		this.displayedSlice.close();



		if (this.method == Method.GLOBAL_ITERATIVE) {
			this.disableStackRoiSelection();
			this.enableRootSelection();
		}
		else {
			//TODO run/save these methods to be run later
			if (this.method == Method.GLOBAL_THRESHOLD) {
				this.plugin.addGlobalThreshold();
			}
			if (this.method == Method.COLOR_BLOBS) {
				this.plugin.addColorBlobs();
			}
			this.dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
		}
		
	}
	
	public void setRootRoi(Roi roi) {
		this.plugin.setRootRoi(roi);
		this.displayedSlice.close();
		this.plugin.addGlobalIterative();
		this.dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
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

class SegmentationListFrame extends BasicFrame {
	ArrayList<SegmentationPlugin> plugins;
	
	JTextArea info;
	JButton startProcessing;
	
	
	public SegmentationListFrame(ArrayList<SegmentationPlugin> pluginsToRun) {
		super();
		this.plugins = pluginsToRun;
		this.info = new JTextArea("Plugins in queue:",30,6);
		this.startProcessing = new JButton("Start Processing");
		this.startProcessing.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				System.out.println("Run button pressed!");
				System.out.println("plugins size is " + SegmentationListFrame.this.plugins.size());

				for (SegmentationPlugin plugin: SegmentationListFrame.this.plugins) {
					System.out.println("For: " + plugin + " - have started running.");
					Thread t = new Thread(plugin);
					t.start();
				}
			}
		});
		
		this.add(info);
		this.add(startProcessing);
		this.setVisible(true);
				
	}
	
	/*public void paintComponent(Graphics g) {
		this.info.setText("");
		for (SegmentationPlugin plugin: plugins) {
			this.info.append(plugin.toString());
		}
		
		
	}*/
	
	public void updateTable(SegmentationPlugin plugin) {
		this.info.append("\n" + plugin);
		this.info.update(this.info.getGraphics());
	}
	
		
}
			




