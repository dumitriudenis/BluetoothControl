package com.example.denis.helper;

import android.graphics.Bitmap;

import org.opencv.core.Mat;

import java.util.Vector;

/**
 * <p/>
 * Java Implementation of the Hough Transform.<br />
 * Used for finding straight lines in an image.<br />
 * by Olly Oechsle
 * </p>
 * <p/>
 * Note: This class is based on original code from:<br />
 * <a href="http://homepages.inf.ed.ac.uk/rbf/HIPR2/hough.htm">http://homepages.inf.ed.ac.uk/rbf/HIPR2/hough.htm</a>
 * </p>
 * <p/>
 * If you represent a line as:<br />
 * x cos(theta) + y sin (theta) = r
 * </p>
 * <p/>
 * ... and you know values of x and y, you can calculate all the values of r by going through all the possible values of theta. If you plot the values of r on a graph for every value of theta you get a sinusoidal curve. This is the Hough transformation.
 * </p>
 * <p/>
 * The hough tranform works by looking at a number of such x,y coordinates, which are usually found by some kind of edge detection. Each of these coordinates is transformed into an r, theta curve. This curve is discretised so we actually only look at a certain discrete number of theta values. "Accumulator" cells in a hough array along this curve are incremented for X and Y coordinate.
 * </p>
 * <p/>
 * The accumulator space is plotted rectangularly with theta on one axis and r on the other. Each point in the array represents an (r, theta) value which can be used to represent a line using the formula above.
 * </p>
 * <p/>
 * Once all the points have been added should be full of curves. The algorithm then searches for local peaks in the array. The higher the peak the more values of x and y crossed along that curve, so high peaks give good indications of a line.
 * </p>
 *
 * @authors Olly Oechsle, University of Essex, Jakub Medveck�-Heretik
 */
public class HoughLineTransform {

    // The size of the neighbourhood in which to search for other local maxima 
    private final int neighbourhoodSize = 4;

    // How many discrete values of theta shall we check? 
    private final int maxTheta = 180;

    // Using maxTheta, work out the step 
    private final double thetaStep = Math.PI / maxTheta;

    // the width and height of the image
    private int width, height;

    // the hough array 
    private int[][] houghArray;

    // the coordinates of the centre of the image 
    private float centerX, centerY;

    // the height of the hough array
    private int houghHeight;

    // double the hough height (allows for negative numbers) 
    private int doubleHeight;

    // the number of points that have been added 
    private int numPoints;

    // cache of values of sin and cos for different theta values. Has a significant performance improvement. 
    private double[] sinCache;
    private double[] cosCache;

    /**
     * Initialises the hough transform. The dimensions of the input image are needed in order to initialise the hough array.
     *
     * @param width The width of the input image
     * @param height The height of the input image
     */
    public HoughLineTransform(int width, int height) {
        this.width = width;
        this.height = height;
        initialise();
    }

    /**
     * Initialises the hough array. Called by the constructor so you don't need to call it yourself, however you can use it to reset the transform if you want to plug in another image (although that image must have the same width and height)
     */
    public void initialise() {

        // Calculate the maximum height the hough array needs to have 
        houghHeight = (int) (Math.sqrt(2) * Math.max(height, width)) / 2;

        // Double the height of the hough array to cope with negative r values 
        doubleHeight = 2 * houghHeight;

        // Create the hough array
        houghArray = new int[maxTheta][doubleHeight];

        // Find edge points and vote in array 
        centerX = width / 2;
        centerY = height / 2;

        // Count how many points there are 
        numPoints = 0;

        // cache the values of sin and cos for faster processing 
        sinCache = new double[maxTheta];
        cosCache = sinCache.clone();
        for (int t = 0; t < maxTheta; t++) {
            double realTheta = t * thetaStep;
            sinCache[t] = Math.sin(realTheta);
            cosCache[t] = Math.cos(realTheta);
        }
    }

    /**
     * Adds points from an image. The image is assumed to be greyscale black and white, so all pixels that are not black are counted as edges. The image should have the same dimensions as the one passed to the constructor.
     */
    public void addPoints(Bitmap image) {

        // Now find edge points and update the hough array 
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                // Find non-black pixels
                if ((image.getPixel(x, y) & 0x000000ff) != 0) {
                    addPoint(x, y);
                }
            }
        }
    }

    /**
     * Adds points from an image (in OpenCV's org.opencv.core.Mat format). The image is assumed to be greyscale black and white, so all pixels that are not black are counted as edges. The image should have the same dimensions as the one passed to the constructor, but only bottom half of the image should contain edges.
     *
     * @author Jakub Medveck�-Heretik
     */
    public void addPoints(Mat image) {

        // Now find edge points and update the hough array 
        for (int x = 0; x < image.cols(); x++) {
            for (int y = image.rows() / 2; y < image.rows(); y++) {

                // Find non-black pixels
                double[] color = image.get(y, x);
                if (color[0] != 0) {
                    addPoint(x, y);
                }
            }
        }
    }

    /**
     * Adds a single point to the hough transform. You can use this method directly if your data isn't represented as a buffered image.
     */
    public void addPoint(int x, int y) {

        // Go through each value of theta 
        for (int t = 0; t < maxTheta; t++) {

            //Work out the r values for each theta step 
            int r = (int) (((x - centerX) * cosCache[t]) + ((y - centerY) * sinCache[t]));

            // this copes with negative values of r 
            r += houghHeight;

            if (r < 0 || r >= doubleHeight) {
                continue;
            }

            // Increment the hough array 
            houghArray[t][r]++;

        }

        numPoints++;
    }

    /*
    * *
     * Once points have been added in some way this method extracts the lines and returns them as a Vector of HoughLine objects, which can be used to draw on the
     *
     * @param percentageThreshold The percentage threshold above which lines are determined from the hough array
     */
    public Vector<HoughLine> getLines(int threshold) {

        // Initialise the vector of lines that we'll return 
        Vector<HoughLine> lines = new Vector<HoughLine>(20);

        // Only proceed if the hough array is not empty 
        if (numPoints == 0) {
            return lines;
        }

        // Search for local peaks above threshold to draw 
        for (int t = 0; t < maxTheta; t++) {
            loop:
            for (int r = neighbourhoodSize; r < doubleHeight - neighbourhoodSize; r++) {

                // Only consider points above threshold 
                if (houghArray[t][r] > threshold) {

                    int peak = houghArray[t][r];

                    // Check that this peak is indeed the local maxima 
                    for (int dx = -neighbourhoodSize; dx <= neighbourhoodSize; dx++) {
                        for (int dy = -neighbourhoodSize; dy <= neighbourhoodSize; dy++) {
                            int dt = t + dx;
                            int dr = r + dy;
                            if (dt < 0) {
                                dt = dt + maxTheta;
                            } else if (dt >= maxTheta) {
                                dt = dt - maxTheta;
                            }
                            if (houghArray[dt][dr] > peak) {
                                // found a bigger point nearby, skip 
                                continue loop;
                            }
                        }
                    }

                    // calculate the true value of theta 
                    double theta = t * thetaStep;

                    // add the line to the vector 
                    lines.add(new HoughLine(theta, r));

                }
            }
        }

        return lines;
    }
}
