importClass(java.io.File);
importClass(javax.swing.JFileChooser);
importClass(Packages.ij.IJ);
importClass(Packages.ij.gui.Roi);

// Ask user to select input file
var fileChooser = new JFileChooser();
fileChooser.setDialogTitle("Select input file");
var returnVal = fileChooser.showOpenDialog(null);
if (returnVal != JFileChooser.APPROVE_OPTION) {
    print("No input file selected. Exiting script.");
    exit();
}
var inputFile = fileChooser.getSelectedFile();

var stack = IJ.getImage();

// Get the dimensions of the stack
var width = stack.getWidth();
var height = stack.getHeight();
var nSlices = stack.getNSlices();

// Prompt the user to select a rectangular ROI
var roi = IJ.getImage().getRoi();
if (roi == null || !(roi.getType() == Roi.RECTANGLE)) {
    IJ.error("A rectangular ROI is required.");
    exit();
}

// Get the coordinates of the rectangular ROI
var x1 = roi.getXBase();
var y1 = roi.getYBase();
var x2 = x1 + roi.getFloatWidth();
var y2 = y1 + roi.getFloatHeight();

var results = [];

// Loop through the Z-axis of the stack
for (var z = 1; z <= nSlices; z++) {

    // Select the current slice
    stack.setSlice(z);

    // Get the pixel values of the current slice
    var pixels = stack.getProcessor().getPixels();

    // Calculate the sum of the pixel values within the ROI
    var sum = 0;
    var count = 0;
    for (var y = y1; y < y2; y++) {
        for (var x = x1; x < x2; x++) {
            var pixelValue = pixels[x + y * width];
            sum += pixelValue;
            count++;
        }
    }

    // Calculate the average pixel value within the ROI
    var avgValue = parseFloat(sum / count);

    // Add the average pixel value to the results array
    results.push(avgValue);
}

// Convert the results array to a Java array
var resultsJava = Java.to(results, "double[]");

// Create a new Plot object for the Z-axis plot profile
var plot = new Plot("Z-axis Plot Profile", "Slice", "Value", null);

// Add the Z-axis values to the plot
plot.add("Z-axis", Arrays.copyOfRange(resultsJava, 0, resultsJava.length));

// Display the plot
plot.show();

// Prompt the user to specify the file name and location to save the plot data
var saveDialog = new SaveDialog("Save Plot Data", "z_axis_plot_profile", ".txt");
var outputFile = new File(saveDialog.getDirectory() + saveDialog.getFileName());

// Read input file and replace second column with zValues
var csv = new Packages.java.util.Scanner(inputFile).useDelimiter("\\A").next();
var lines = csv.split("\n");
var newCsv = "";
for (var i = 0; i < lines.length; i++) {
    var cols = lines[i].split(",");
    if (cols.length >= 2) {
        cols[1] = "" + results[i];
        newCsv += cols.join(",") + "\n";
    } else {
        newCsv += lines[i] + "\n";
    }
}

// Save output file
var fileWriter = new Packages.java.io.FileWriter(outputFile);
fileWriter.write(newCsv);
fileWriter.flush();
fileWriter.close();

