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


public class ColorBlobOptionsFrame extends JFrame {
	Segmenter_Comparison sc;
	JButton run;
	JButton postProcessing;
	JTextField rootLowerBound;
	JTextField rootUpperBound;
	JCheckBox printMatches;
	JCheckBox printDifferences;
	
	JTextField minSliceNumber;
	JTextField maxSliceNumber;
	JTextField minValue;
	JTextField maxValue;
	JTextField minCenterX;
	JTextField minCenterY;
	JTextField maxCenterX;
	JTextField maxCenterY;
	JTextField minArea, maxArea;
	JTextField chainJoiningScaler;
	JTextField majorMinorRatioLimit;

	JTextField colourDifferenceWeight, areaDifferenceWeight, aspectRatioDifferenceWeight;
	JTextField minClusterSize, minClusterChainLength, maxCenterDistance;
	
	
	
	public ColorBlobOptionsFrame(Segmenter_Comparison sc) {
		this.sc = sc;
		
		this.setTitle("Select limits");
		this.setSize(550,750);
		this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		this.setLayout(new FlowLayout());
		
		this.add(new JLabel("Root lower bound (5 bit)"));
		this.rootLowerBound = new JTextField("", 3);
		this.add(rootLowerBound);
		
		this.add(new JLabel("Root upper bound (5 bit)"));
		this.rootUpperBound = new JTextField("", 3);
		this.add(rootUpperBound);
		
		this.printMatches = new JCheckBox("Print matches?", false);
		this.add(printMatches);
		this.printDifferences = new JCheckBox("Print differences?", false);
		this.printDifferences.setEnabled(false);
		this.add(printDifferences);
		
		JPanel limitPanel = new JPanel();
		limitPanel.setLayout(new BoxLayout(limitPanel, BoxLayout.Y_AXIS));
		this.add(limitPanel);
		
	
		limitPanel.add(new JLabel("min slice number"));
		this.minSliceNumber = new JTextField("", 3);
		this.minSliceNumber.setEnabled(false);
		limitPanel.add(minSliceNumber);
		
		limitPanel.add(new JLabel("max slice number"));
		this.maxSliceNumber = new JTextField("", 3);
		this.maxSliceNumber.setEnabled(false);
		limitPanel.add(maxSliceNumber);
		
		limitPanel.add(new JLabel("min value"));
		this.minValue = new JTextField("", 3);
		this.minValue.setEnabled(false);
		limitPanel.add(minValue);
		
		limitPanel.add(new JLabel("maxValue"));
		this.maxValue = new JTextField("", 3);
		this.maxValue.setEnabled(false);
		limitPanel.add(maxValue);
		
		limitPanel.add(new JLabel("minArea"));
		this.minArea = new JTextField("", 3);
		this.minArea.setEnabled(false);
		limitPanel.add(minArea);
		
		limitPanel.add(new JLabel("maxArea"));
		this.maxArea = new JTextField("", 3);
		this.maxArea.setEnabled(false);
		limitPanel.add(maxArea);
		
		limitPanel.add(new JLabel("minCenterX"));
		this.minCenterX = new JTextField("", 3);
		this.minCenterX.setEnabled(false);
		limitPanel.add(minCenterX);
		
		limitPanel.add(new JLabel("maxCenterX"));
		this.maxCenterX = new JTextField("", 3);
		this.maxCenterX.setEnabled(false);
		limitPanel.add(maxCenterX);
		
		limitPanel.add(new JLabel("minCenterY"));
		this.minCenterY = new JTextField("", 3);
		this.minCenterY.setEnabled(false);
		limitPanel.add(minCenterY);
		
		limitPanel.add(new JLabel("maxCenterY"));
		this.maxCenterY = new JTextField("", 3);
		this.maxCenterY.setEnabled(false);
		limitPanel.add(maxCenterY);
		
		this.run = new JButton("RUN");

		this.run.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				System.out.println("Run action performed");
				int rootLowerBound = Integer.parseInt(ColorBlobOptionsFrame.this.rootLowerBound.getText());
				int rootUpperBound = Integer.parseInt(ColorBlobOptionsFrame.this.rootUpperBound.getText());
				boolean printMatches = ColorBlobOptionsFrame.this.printMatches.isSelected();
				int minSliceNumber, maxSliceNumber, minValue, maxValue;
				int minArea, maxArea, minCenterX, maxCenterX, minCenterY, maxCenterY;
				boolean printDifferences;

				int minClusterSize = Integer.parseInt(ColorBlobOptionsFrame.this.minClusterSize.getText());
				int minClusterChainLength = Integer.parseInt(ColorBlobOptionsFrame.this.minClusterChainLength.getText());
				float majorMinorRatioLimit = Float.parseFloat(ColorBlobOptionsFrame.this.majorMinorRatioLimit.getText());
				float chainJoiningScaler = Float.parseFloat(ColorBlobOptionsFrame.this.chainJoiningScaler.getText());
				int maxCenterDistance = Integer.parseInt(ColorBlobOptionsFrame.this.maxCenterDistance.getText());
				float areaDifferenceWeight = Float.parseFloat(ColorBlobOptionsFrame.this.areaDifferenceWeight.getText());
				float colourDifferenceWeight = Float.parseFloat(ColorBlobOptionsFrame.this.colourDifferenceWeight.getText());
				float aspectRatioDifferenceWeight = Float.parseFloat(ColorBlobOptionsFrame.this.aspectRatioDifferenceWeight.getText());

				ColorBlobOptionsFrame.this.run.setEnabled(false);
				ColorBlobOptionsFrame.this.postProcessing.setEnabled(false);

				if (printMatches) {


					minSliceNumber = Integer.parseInt(ColorBlobOptionsFrame.this.minSliceNumber.getText());
					maxSliceNumber = Integer.parseInt(ColorBlobOptionsFrame.this.maxSliceNumber.getText());
					minValue = Integer.parseInt(ColorBlobOptionsFrame.this.minValue.getText());
					maxValue = Integer.parseInt(ColorBlobOptionsFrame.this.maxValue.getText());
					minArea = Integer.parseInt(ColorBlobOptionsFrame.this.minArea.getText());
					maxArea = Integer.parseInt(ColorBlobOptionsFrame.this.maxArea.getText());
					minCenterX = Integer.parseInt(ColorBlobOptionsFrame.this.minCenterX.getText());
					maxCenterX = Integer.parseInt(ColorBlobOptionsFrame.this.maxCenterX.getText());
					minCenterY = Integer.parseInt(ColorBlobOptionsFrame.this.minCenterY.getText());
					maxCenterY = Integer.parseInt(ColorBlobOptionsFrame.this.maxCenterY.getText());
					printDifferences = ColorBlobOptionsFrame.this.printDifferences.isSelected();

					ColorBlobOptions optionsWithLimits = new ColorBlobOptions(rootLowerBound, rootUpperBound, printMatches,
						minClusterSize, maxCenterDistance, areaDifferenceWeight, aspectRatioDifferenceWeight,
						colourDifferenceWeight, minClusterChainLength, majorMinorRatioLimit, chainJoiningScaler,
						minSliceNumber, maxSliceNumber, minValue, maxValue, minArea, maxArea,
						minCenterX, maxCenterX, minCenterY, maxCenterY, printDifferences);
					System.out.println("It was true, now about to run.");
					ColorBlobOptionsFrame.this.setOptions(optionsWithLimits);
				}				
				else {

					ColorBlobOptions optionsNoLimits = new ColorBlobOptions(rootLowerBound, rootUpperBound, printMatches,
						minClusterSize, maxCenterDistance, areaDifferenceWeight, aspectRatioDifferenceWeight,
						colourDifferenceWeight, minClusterChainLength, majorMinorRatioLimit, chainJoiningScaler);
					System.out.println("It was false, now about to run");
					ColorBlobOptionsFrame.this.setOptions(optionsNoLimits);
				}
				System.out.println("done in actionListener");
			}
		});

		this.add(run);

		
		this.printMatches.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				System.out.println("Toggling boxes.");
				boolean selected = ColorBlobOptionsFrame.this.printMatches.isSelected();
				ColorBlobOptionsFrame.this.minSliceNumber.setEnabled(selected);
				ColorBlobOptionsFrame.this.maxSliceNumber.setEnabled(selected);
				ColorBlobOptionsFrame.this.minValue.setEnabled(selected);
				ColorBlobOptionsFrame.this.maxValue.setEnabled(selected);
				ColorBlobOptionsFrame.this.minArea.setEnabled(selected);
				ColorBlobOptionsFrame.this.maxArea.setEnabled(selected);
				ColorBlobOptionsFrame.this.minCenterX.setEnabled(selected);
				ColorBlobOptionsFrame.this.maxCenterX.setEnabled(selected);
				ColorBlobOptionsFrame.this.minCenterY.setEnabled(selected);
				ColorBlobOptionsFrame.this.maxCenterY.setEnabled(selected);
				ColorBlobOptionsFrame.this.printDifferences.setEnabled(selected);
			}
		});
		
		JPanel constantsPanel = new JPanel();
		constantsPanel.setLayout(new BoxLayout(constantsPanel, BoxLayout.Y_AXIS));
		this.add(constantsPanel);


		constantsPanel.add(new JLabel("minClusterSize"));
		this.minClusterSize = new JTextField("20", 5);
		constantsPanel.add(minClusterSize);
		
		constantsPanel.add(new JLabel("maxCenterDistance"));
		this.maxCenterDistance = new JTextField("5", 5);
		constantsPanel.add(maxCenterDistance);

		constantsPanel.add(new JLabel("areaDifferenceWeight"));
		this.areaDifferenceWeight = new JTextField("5.0", 5);
		constantsPanel.add(areaDifferenceWeight);

		constantsPanel.add(new JLabel("colourDifferenceWeight"));
		this.colourDifferenceWeight = new JTextField("0.1", 5);
		constantsPanel.add(colourDifferenceWeight);

		constantsPanel.add(new JLabel("aspectRatioDifferenceWeight"));
		this.aspectRatioDifferenceWeight = new JTextField("1.0", 5);
		constantsPanel.add(aspectRatioDifferenceWeight);



		
		JPanel postProcessPanel = new JPanel();
		postProcessPanel.setLayout(new BoxLayout(postProcessPanel, BoxLayout.Y_AXIS));
		this.add(postProcessPanel);
		postProcessPanel.add(new JLabel("Modify post-processing constants"));
		
		postProcessPanel.add(new JLabel("minClusterChainLength"));
		this.minClusterChainLength = new JTextField("15", 5);
		postProcessPanel.add(minClusterChainLength);
		
		postProcessPanel.add(new JLabel("chainJoiningScaler"));
		this.chainJoiningScaler = new JTextField("1", 5);
		postProcessPanel.add(chainJoiningScaler);
		
		postProcessPanel.add(new JLabel("majorMinorRatioLimit"));
		this.majorMinorRatioLimit = new JTextField("2", 5);
		postProcessPanel.add(majorMinorRatioLimit);
		
		this.postProcessing = new JButton("Run Post-Processing only");
		postProcessPanel.add(postProcessing);
		this.postProcessing.setEnabled(false);
		
		this.postProcessing.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int minClusterChainLength = Integer.parseInt(ColorBlobOptionsFrame.this.minClusterChainLength.getText());
				float majorMinorRatioLimit = Float.parseFloat(ColorBlobOptionsFrame.this.majorMinorRatioLimit.getText());
				float chainJoiningScaler = Float.parseFloat(ColorBlobOptionsFrame.this.chainJoiningScaler.getText());
				ColorBlobOptionsFrame.this.runPostProcessing(chainJoiningScaler, majorMinorRatioLimit, minClusterChainLength);
			}
		});
		
		this.setVisible(true);
		System.out.println("Made it visible!");
	}

	public void enableRun() {
		this.run.setEnabled(true);
		this.postProcessing.setEnabled(true);
	}
	
	public void setOptions(ColorBlobOptions options) {
		this.sc.addCS(options);
		
	}
	
	
	public void runPostProcessing(float chainJoiningScaler, float majorMinorRatioLimit, int minClusterChainLength) {
		
		
	}
}






