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

public class GlobalIterativeOptionsFrame extends JFrame {
	Segmenter_Comparison sc;
	JTextField stdDev;
	JTextField EDT_Threshold;
	JButton run;
	
	public GlobalIterativeOptionsFrame(Segmenter_Comparison sc) {
		this.sc = sc;
		
		this.setTitle("Select GlobalIterative options");
		this.setSize(500,500);
		this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		this.setLayout(new FlowLayout());
		
		this.add(new JLabel("Std_dev: " ));
		this.stdDev = new JTextField("5", 3);
		this.add(stdDev);
		
		this.add(new JLabel("EDT Threshold"));
		this.EDT_Threshold = new JTextField("5", 3);
		this.add(EDT_Threshold);
		
		this.run = new JButton("Apply options");
		this.run.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				System.out.println("Run action performed");
				float stdDev = Float.parseFloat(GlobalIterativeOptionsFrame.this.stdDev.getText());
				int EDT_Threshold = Integer.parseInt(GlobalIterativeOptionsFrame.this.EDT_Threshold.getText());
				
				Runnable r = new Runnable() {
					public void run() {
						GlobalIterativeOptionsFrame.this.sc.addGI(stdDev, EDT_Threshold);
					}
				};
				Thread t = new Thread(r);
				t.start();
				GlobalIterativeOptionsFrame.this.dispatchEvent(new WindowEvent(GlobalIterativeOptionsFrame.this, WindowEvent.WINDOW_CLOSING));

				
			}
		});
		this.add(run);
		
		this.setVisible(true);
	}
}
	