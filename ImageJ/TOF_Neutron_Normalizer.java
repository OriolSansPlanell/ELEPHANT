import ij.*;
import ij.plugin.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;
import ij.text.*;
import java.awt.*;

/**
 * ImageJ Plugin for normalizing time-of-flight neutron radiographies
 * 
 * This plugin normalizes the intensity across a stack of radiographies
 * based on the average intensity of a user-selected region.
 * 
 * @author Dr. Oriol Sans Planell
 * @version 1.0
 */
public class TOF_Neutron_Normalizer implements PlugIn {
    
    public void run(String arg) {
        // Get the current image
        ImagePlus imp = IJ.getImage();
        if (imp == null) {
            IJ.error("TOF Neutron Normalizer", "No image is open.");
            return;
        }
        
        // Check if it's a stack
        ImageStack stack = imp.getStack();
        if (stack.getSize() < 2) {
            IJ.error("TOF Neutron Normalizer", "This plugin requires an image stack.");
            return;
        }
        
        // Check if there's a selection
        Roi roi = imp.getRoi();
        if (roi == null) {
            IJ.error("TOF Neutron Normalizer", 
                    "Please make a selection (rectangle, oval, or freehand) before running this plugin.");
            return;
        }
        
        // Show dialog for normalization options
        GenericDialog gd = new GenericDialog("TOF Neutron Normalizer");
        gd.addMessage("Normalize stack based on selected region intensity");
        gd.addChoice("Normalization method:", 
                    new String[]{"Divide by mean", "Scale to target value", "Normalize to first image"}, 
                    "Divide by mean");
        gd.addNumericField("Target value (for scaling method):", 1000, 0);
        gd.addCheckbox("Create new stack (uncheck to modify current)", true);
        gd.addCheckbox("Show intensity plot", false);
        gd.showDialog();
        
        if (gd.wasCanceled()) return;
        
        String method = gd.getNextChoice();
        double targetValue = gd.getNextNumber();
        boolean createNew = gd.getNextBoolean();
        boolean showPlot = gd.getNextBoolean();
        
        // Process the stack
        processStack(imp, roi, method, targetValue, createNew, showPlot);
    }
    
    private void processStack(ImagePlus imp, Roi roi, String method, 
                            double targetValue, boolean createNew, boolean showPlot) {
        
        ImageStack originalStack = imp.getStack();
        int nSlices = originalStack.getSize();
        int width = imp.getWidth();
        int height = imp.getHeight();
        
        // Array to store mean intensities
        double[] meanIntensities = new double[nSlices];
        
        // Calculate mean intensity for each slice in the selected region
        IJ.showStatus("Calculating mean intensities...");
        for (int i = 1; i <= nSlices; i++) {
            IJ.showProgress(i, nSlices * 2); // First half of progress
            
            ImageProcessor ip = originalStack.getProcessor(i);
            ip.setRoi(roi);
            
            // Calculate mean intensity in ROI
            ImageStatistics stats = ImageStatistics.getStatistics(ip, 
                                                                Measurements.MEAN, 
                                                                imp.getCalibration());
            meanIntensities[i-1] = stats.mean;
        }
        
        // Determine normalization factors
        double[] normFactors = calculateNormalizationFactors(meanIntensities, method, targetValue);
        
        // Create new stack or modify existing
        ImageStack resultStack;
        ImagePlus resultImp;
        
        if (createNew) {
            resultStack = new ImageStack(width, height);
            for (int i = 1; i <= nSlices; i++) {
                ImageProcessor ip = originalStack.getProcessor(i).duplicate();
                resultStack.addSlice(originalStack.getSliceLabel(i), ip);
            }
            resultImp = new ImagePlus(imp.getTitle() + "_normalized", resultStack);
            resultImp.setCalibration(imp.getCalibration());
        } else {
            resultStack = originalStack;
            resultImp = imp;
        }
        
        // Apply normalization
        IJ.showStatus("Applying normalization...");
        for (int i = 1; i <= nSlices; i++) {
            IJ.showProgress(nSlices + i, nSlices * 2); // Second half of progress
            
            ImageProcessor ip = resultStack.getProcessor(i);
            
            // Apply normalization factor
            if (method.equals("Divide by mean")) {
                ip.multiply(1.0 / normFactors[i-1]);
            } else if (method.equals("Scale to target value") || method.equals("Normalize to first image")) {
                ip.multiply(normFactors[i-1]);
            }
        }
        
        // Show result if new stack was created
        if (createNew) {
            resultImp.show();
        } else {
            imp.updateAndDraw();
        }
        
        // Show intensity plot if requested
        if (showPlot) {
            showIntensityPlot(meanIntensities, normFactors, method);
        }
        
        // Show summary
        showSummary(meanIntensities, normFactors, method);
        
        IJ.showStatus("Normalization complete.");
        IJ.showProgress(1.0);
    }
    
    private double[] calculateNormalizationFactors(double[] meanIntensities, 
                                                  String method, double targetValue) {
        double[] factors = new double[meanIntensities.length];
        
        if (method.equals("Divide by mean")) {
            // Factor is the mean intensity itself (we'll divide by it)
            System.arraycopy(meanIntensities, 0, factors, 0, meanIntensities.length);
            
        } else if (method.equals("Scale to target value")) {
            // Factor to scale each image so its mean becomes targetValue
            for (int i = 0; i < meanIntensities.length; i++) {
                factors[i] = (meanIntensities[i] != 0) ? targetValue / meanIntensities[i] : 1.0;
            }
            
        } else if (method.equals("Normalize to first image")) {
            // Factor to scale each image so its mean matches the first image's mean
            double firstImageMean = meanIntensities[0];
            for (int i = 0; i < meanIntensities.length; i++) {
                factors[i] = (meanIntensities[i] != 0) ? firstImageMean / meanIntensities[i] : 1.0;
            }
        }
        
        return factors;
    }
    
    private void showIntensityPlot(double[] meanIntensities, double[] normFactors, String method) {
        // Create arrays for slice numbers
        double[] sliceNumbers = new double[meanIntensities.length];
        for (int i = 0; i < sliceNumbers.length; i++) {
            sliceNumbers[i] = i + 1;
        }
        
        // Create plot
        Plot plot = new Plot("ROI Mean Intensities", "Slice Number", "Mean Intensity");
        plot.add("line", sliceNumbers, meanIntensities);
        plot.setColor(Color.BLUE);
        plot.addPoints(sliceNumbers, meanIntensities, Plot.CIRCLE);
        
        // Add normalization info to plot
        if (method.equals("Scale to target value") || method.equals("Normalize to first image")) {
            double[] normalizedIntensities = new double[meanIntensities.length];
            for (int i = 0; i < meanIntensities.length; i++) {
                normalizedIntensities[i] = meanIntensities[i] * normFactors[i];
            }
            plot.setColor(Color.RED);
            plot.add("line", sliceNumbers, normalizedIntensities);
            plot.addLegend("Original\nNormalized");
        }
        
        plot.show();
    }
    
    private void showSummary(double[] meanIntensities, double[] normFactors, String method) {
        // Calculate statistics
        double minIntensity = meanIntensities[0];
        double maxIntensity = meanIntensities[0];
        double sumIntensity = 0;
        
        for (double intensity : meanIntensities) {
            if (intensity < minIntensity) minIntensity = intensity;
            if (intensity > maxIntensity) maxIntensity = intensity;
            sumIntensity += intensity;
        }
        
        double meanOfMeans = sumIntensity / meanIntensities.length;
        double range = maxIntensity - minIntensity;
        double cv = (range / meanOfMeans) * 100; // Coefficient of variation as percentage
        
        // Show results window
        String summary = "TOF Neutron Normalizer - Summary\n" +
                        "================================\n" +
                        "Method: " + method + "\n" +
                        "Number of slices: " + meanIntensities.length + "\n\n" +
                        "ROI Mean Intensities (before normalization):\n" +
                        "  Minimum: " + IJ.d2s(minIntensity, 3) + "\n" +
                        "  Maximum: " + IJ.d2s(maxIntensity, 3) + "\n" +
                        "  Average: " + IJ.d2s(meanOfMeans, 3) + "\n" +
                        "  Range: " + IJ.d2s(range, 3) + "\n" +
                        "  Coefficient of Variation: " + IJ.d2s(cv, 2) + "%\n";
        
        new TextWindow("Normalization Summary", summary, 400, 300);
    }
}