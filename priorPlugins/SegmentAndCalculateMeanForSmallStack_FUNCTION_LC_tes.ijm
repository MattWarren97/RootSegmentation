function SegmentAndCalculateMean(image_name,X,Y,Z,init_mean,init_std,rm_out,med_size,max_std) {
	//RAW IMAGE PATH IS IMAGE TO APPLY SEG TO
	//X,Y,Z ARE WIDTH, HEIGHT, NUMBER OF SLICES RESP.
	//init_mean and init_std are the mean and std of grey values of roots from the first slice
	//rm_out is the size of the remove outlier filter throughout
	//med_size if the size of the 2D median filter used throughout.
	//RETURNS A POINT IN THE MASK, A WINDOW CALLED "Mask" which is the resulting segmentation, and a window called "OriginalStack" which is what we segemented

	setBatchMode(true);
	//run("Raw...", "open="+RAW_image_path+" image=8-bit width="+X+" height="+Y+" number="+Z+" little-endian");

	selectWindow(image_name);	
	rename("OriginalStack");
	extra_X = X+med_size;
	extra_Y = Y+med_size;
	run("Canvas Size...", "width="+extra_X+" height="+extra_Y+" position=Center zero");
	run("Set Measurements...", "mean standard limit redirect=None decimal=3");
	selectWindow("OriginalStack");
	TotalSlices=nSlices;
	setSlice(1);
	run("Duplicate...", "title=TopSlice1");
	run("Duplicate...", "title=TopSlice2");
	SD=init_std; //FORMAT
	M=init_mean; //FORMAT
	run("Macro...", "code=v=(1/("+SD+"*sqrt(2*3.14)))*exp((-(v-"+M+")*(v-"+M+"))/(2*"+SD+"*"+SD+"))/(1/("+SD+"*sqrt(2*3.14)))*exp((0)/(2*("+SD+"*"+SD+")))*255");
//gaussian function --> any value close to M (our current guess at the mean --which is probably updated each time this function is run to be the previous guess at the mean [so the greyscale intensity of the roots can be allowed to change as the image gets deeper])
//so, any value close (close relative to the S.D in some way) to M will go closer to white, anything not near that mean will be mapped to a value close to black.
//this is applied to every pixel (I'm not sure how that's specified. This might work sort of like a lambda function where a pixel's intensity is passed in)
//What I think this does: Similar to using thresholds, but you maintain some greyscale variance.
//But: Next thing that's done -- "setThreshold" -- not entirely sure what that's for.
//Then: "Convert to Mask" -- If the thresholds were set, I would have thought it would already be binary for each pixel (and not greyscale anymore) -- so what does that do?

//"setThreshold" will either actually modify the image based on those thresholds, or it won't do anything to the image. In either case, this doesn't really make much sense.
//Why would you use a gaussian function, then convert to mask -- does that not do the same as just setting a threshold?


		setThreshold(75, 255);
		run("Convert to Mask");
		run("Invert LUT");
	run("Median...", "radius="+med_size); //FORMAT

	run("Clear Results");
	run("Remove Outliers...", "radius="+rm_out+" threshold=50 which=Bright slice");
	run("Find Maxima...", "noise=0 output=List"); //Results now has location of white
	non_zeroX= getResult("X",0);
	non_zeroY=getResult("Y",0);
	centre=newArray(non_zeroX,non_zeroY);
	run("Clear Results");
	run("Invert");
	selectWindow("TopSlice1");
	imageCalculator("Subtract create", "TopSlice1","TopSlice2");
	selectWindow("Result of TopSlice1");
	setThreshold(1,255);
	//call("ij.plugin.frame.ThresholdAdjuster.setMode", "Over/Under");
	run("Measure");
	resetThreshold();
	selectWindow("TopSlice2");
	rename("MaskSlice1");
	selectWindow("Result of TopSlice1");
	rename("ImageSlice1");
	selectWindow("TopSlice1");
	//close();
	selectWindow("ImageSlice1");
	
	selectWindow("OriginalStack");
	setSlice(2);
	run("Duplicate...", "title=Slice1");
	selectWindow("OriginalStack");
	setSlice(2);
	run("Duplicate...", "title=Slice");
	//selectWindow("Result of Slice1");
	//selectWindow("ImageSlice1");
	//SD=getResult("StdDev");
	M=getResult("Mean");
	run("Macro...", "code=v=(1/("+SD+"*sqrt(2*3.14)))*exp((-(v-"+M+")*(v-"+M+"))/(2*"+SD+"*"+SD+"))/(1/("+SD+"*sqrt(2*3.14)))*exp((0)/(2*("+SD+"*"+SD+")))*255"); 
		setThreshold(75, 255);
		run("Convert to Mask");
		run("Invert LUT");
	run("Median...", "radius="+med_size); //FORMAT
	rename("MaskSlice2");
	run("Invert");
	selectWindow("Slice1");
	selectWindow("MaskSlice2");
	imageCalculator("Subtract create", "Slice1","MaskSlice2");
	selectWindow("Result of Slice1");
	rename("ImageSlice2");
	selectWindow("MaskSlice2");
	run("Duplicate...", "title=MaskSlice2-1");
	//Comment out the Concatenate command that doesn't work depending on fiji version
	//run("Concatenate...", "  title=Mask image1=MaskSlice1 image2=MaskSlice2 image3=[-- None --]");
	run("Concatenate...", "stack1=MaskSlice1 stack2=MaskSlice2 title=Mask");
	setBatchMode(true);
	//run("Concatenate...", "  title=ImageSlices image1=ImageSlice1 image2=ImageSlice2 image3=[-- None --]");
	run("Concatenate...", "stack1=ImageSlice1 stack2=ImageSlice2 title=ImageSlices");
	
	selectWindow("ImageSlices");
	setSlice(1);
	setThreshold(1,255);
	//call("ij.plugin.frame.ThresholdAdjuster.setMode", "Over/Under");
	run("Measure");
	
	selectWindow("OriginalStack");
	setSlice(3);
	run("Duplicate...", "title=Slice");
	selectWindow("Slice");
	//SD=getResult("StdDev");
	M=getResult("Mean");
	run("Macro...", "code=v=(1/("+SD+"*sqrt(2*3.14)))*exp((-(v-"+M+")*(v-"+M+"))/(2*"+SD+"*"+SD+"))/(1/("+SD+"*sqrt(2*3.14)))*exp((0)/(2*("+SD+"*"+SD+")))*255");
	
	run("Median...", "radius="+med_size); //FORMAT
	run("Make Binary");
	run("Remove Outliers...", "radius="+rm_out+" threshold=50 which=Bright slice");
	//run("Fill Holes");
	run("Invert");
	rename("MaskSlice2");
	selectWindow("OriginalStack");
	setSlice(3);
	run("Duplicate...", "title=Slice");
	imageCalculator("Subtract create", "Slice","MaskSlice2");
	rename("ImageSlice2");
	//Comment out the Concatenate command that doesn't work depending on fiji version
	//run("Concatenate...", "  title=Mask image1=Mask image2=MaskSlice2 image3=[-- None --]");
	run("Concatenate...", "stack1=Mask stack2=MaskSlice2 title=Mask");
	//run("Concatenate...", "  title=ImageSlices image1=ImageSlices image2=ImageSlice2 image3=[-- None --]");
	run("Concatenate...", "stack1=ImageSlices stack2=ImageSlice2 title=ImageSlices");
	selectWindow("Slice");
	close();
	selectWindow("Slice1");
	close();
	selectWindow("MaskSlice2-1");
	close();
	for (i=3; i<TotalSlices; i++) {
		//for (i=3; i<=300; i++) {
		selectWindow("OriginalStack");
		setSlice(i);
		run("Duplicate...", "title=Slice");
		selectWindow("Slice");
		SD=init_std;
		SD1=getResult("StdDev");
		M1=getResult("Mean");
		//if (SD1<max_std){
		M=M1;
		//}
		//print(M);
		run("Macro...", "code=v=(1/("+SD+"*sqrt(2*3.14)))*exp((-(v-"+M+")*(v-"+M+"))/(2*"+SD+"*"+SD+"))/(1/("+SD+"*sqrt(2*3.14)))*exp((0)/(2*("+SD+"*"+SD+")))*255");
		setThreshold(40, 255);
		run("Convert to Mask");
		run("Invert LUT");
		run("Median...", "radius="+med_size);
		run("Remove Outliers...", "radius="+rm_out+" threshold=50 which=Bright slice");
		run("Invert");
		rename("MaskSlice2");
		selectWindow("OriginalStack");
		setSlice(i);
		run("Duplicate...", "title=Slice");
		imageCalculator("Subtract create", "Slice","MaskSlice2");
		rename("ImageSlice2");
		//Comment out the Concatenate command that doesn't work depending on fiji version
		//run("Concatenate...", "  title=Mask image1=Mask image2=MaskSlice2 image3=[-- None --]");
		run("Concatenate...", "stack1=Mask stack2=MaskSlice2 title=Mask");
		//run("Concatenate...", "  title=ImageSlices image1=ImageSlices image2=ImageSlice2 image3=[-- None --]");
		run("Concatenate...", "stack1=ImageSlices stack2=ImageSlice2 title=ImageSlices");
		selectWindow("Slice");
		close();
		selectWindow("ImageSlices");
		setSlice(i+1);
		setThreshold(1,255);
		//call("ij.plugin.frame.ThresholdAdjuster.setMode", "Over/Under");
		run("Measure");
	}
	selectWindow("Mask");
	run("Invert LUT");
	run("Invert", "stack");
	//rename("MASK1");
	//run("Duplicate...", "duplicate");
	//rename("MASK");
	setBatchMode(false);
	return centre;

}

function Z_PROJ(mask_name,dilate_no,rm_outmask,centre) {
	//setBatchMode(true);
	//Creates Z_PROJ, removes outliers, erodes erode_no times, dilates ceil(erode_no/3)
	//Output is a window called "2DROOTS"
	//setBatchMode(true);
	selectWindow(mask_name);
	run("Distance Transform 3D");
	setThreshold(0.000000000, dilate_no);
	setOption("BlackBackground", false);
	run("Convert to Mask", "method=Default background=Default black");
	//run("Z Project...", "projection=[Max Intensity]");
	//run("Remove Outliers...", "radius="+rm_outmask+" threshold=50 which=Bright");
	//setOption("BlackBackground", false);
	//for (i=1; i<=dilate_no; i++) {
	//	run("Dilate (3D)", "iso=255");
	//}
	//div=dilate_no/5;
	//erode_no = floor(div);
	//for (i=1; i<=erode_no; i++) {
	//	run("Erode (3D)", "iso=255");
	//}
	makePoint(centre[0],centre[1]);
	run("Find Connected Regions", "allow_diagonal display_image_for_each display_results start_from_point regions_for_values_over=100 minimum_number_of_points=1 stop_after=1");
	rename("2DROOTS");
	run("Divide...", "value=255.000 stack");
	//setBatchMode(false);	

}

//MAIN:
//RAW_image_path="\\\\Roose_Group\\S_I_Ahmed\\Laura_data\\20131128_HUTCH_499_SIA_5050_03T_recon\\20131128_HUTCH_499_SIA_5050_03T_1100x1100x1700_8bt.raw"
X=1100;
Y=1100;
Z=300;
//run("Raw...", "open="+RAW_image_path+" image=8-bit width="+X+" height="+Y+" number="+Z+" little-endian");
//rename("OriginalStack1")
//run("Duplicate...", "title=OriginalStack duplicate");
init_mean=89.6;
init_std=4;
med_size=5;
rm_out=4;
//centre=SegmentAndCalculateMean("OriginalStack",X,Y,Z,init_mean,init_std,rm_out,med_size);
//centre=newArray(254,334)

//test Z_PROJ
//Z_PROJ("Mask",8,4,centre) 

SD_Array = newArray(4,5,6);
Erode_Array=newArray(19,7,3);
max_std = newArray(8,9,10);
setBatchMode(false);
for (i=0;i<1;i++) {
	centre=SegmentAndCalculateMean("OriginalStack",X,Y,Z,init_mean,SD_Array[i],rm_out,med_size,max_std[i]);
	selectWindow("Mask");
	rename("Mask_SD_"+SD_Array[i]);
	Z_PROJ("Mask_SD_"+SD_Array[i],Erode_Array[i],3,centre);
	imageCalculator("Multiply create stack","OriginalStack1","2DROOTS");
	close("OriginalStack");
	selectWindow("Result of OriginalStack1");
	rename("OriginalStack");
	close("2DROOTS");
		
}

//Threshold 300-end of stack as -15 +15 last mean 
//Median 3D 5 5 10
//Concatenate stacks
//Distance transform?
//Connected regions