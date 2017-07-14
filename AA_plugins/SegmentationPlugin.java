package AA_plugins;



public abstract class SegmentationPlugin {
	
	ImagePlus image;
	int X;
	int Y;
	int Z;
	SelectionPlugin selectionPlugin;
	FilterPlugin filterPlugin;
	
	public SegmentationPlugin() {
		System.err.println("Initialising log");
		selectionPlugin = new SelectionPlugin();
		filterPlugin = new FilterPlugin();
	}
	
	//Called by the system when the plugin is run. [arg is selected by the user at that time]
	public int setup(String arg, ImagePlus imp) {
		this.image = imp;
		this.X = image.getWidth();
		this.Y = image.getHeight();
		this.Z = image.getStackSize();
		if (arg.equals("about")) {
			showAbout();
			return DONE; //These enums are defined in PlugInFilter.
		}
		return DOES_8G+SUPPORTS_MASKING;
	}
	
	public abstract void showAbout();
	
	
}