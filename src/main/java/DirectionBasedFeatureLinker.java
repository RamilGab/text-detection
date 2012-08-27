import static com.googlecode.javacv.cpp.opencv_core.*;
import static com.googlecode.javacv.cpp.opencv_highgui.cvShowImage;
import static com.googlecode.javacv.cpp.opencv_highgui.cvWaitKey;
import static com.googlecode.javacv.cpp.opencv_imgproc.*;

import java.util.*;

import com.googlecode.javacv.cpp.opencv_core.IplImage;

public class DirectionBasedFeatureLinker extends FeatureLinker {
	private int dilSize;
	
	private CvMat[] filters;
	private int[] filterSums;
	private double[] angles;
	
	private HashMap<Feature, double[]> hists;

	public DirectionBasedFeatureLinker(int size, int dilSize, int numFilters,
			int width) {
		this.dilSize = dilSize;

		this.filters = new CvMat[numFilters];
		this.filterSums = new int[numFilters];
		this.angles = new double[numFilters];

		for (int i = 0; i < numFilters; i++) {
			filters[i] = CvMat.create(size, size, CV_32FC1);
			cvSetZero(filters[i]);
		}

		double r = size / 2;
		int c = (int) Math.round(r);
		for (int i = 0; i < numFilters; i++) {
			double angle = i / (double) numFilters * Math.PI;
			double sin = Math.sin(angle);
			double cos = Math.cos(angle);

			int x = (int) Math.round(cos * r);
			int y = (int) Math.round(sin * r);

			cvDrawLine(filters[i], cvPoint(c + x, c + y),
					cvPoint(c - x, c - y), CvScalar.ONE, width, 8, 0);
			angles[i] = angle;
		}

		for (int i = 0; i < numFilters; i++) {
			filterSums[i] = (int) cvSum(filters[i]).blue();
		}
	}

	@Override
	public FeatureSet linkFeatures(List<Feature> features, IplImage img) {
		IplImage input;
		IplImage direction;
		IplImage max;
		IplImage mask;
		IplImage temp32f;

		// initialize images
		{
			int width = img.width();
			int height = img.height();

			input = IplImage.create(width, height, IPL_DEPTH_32F, 1);
			cvSet(input, CvScalar.ONE);
			cvMul(input, img, input, 1 / 255.0);

			direction = IplImage.create(width, height, IPL_DEPTH_8U, 1);
			cvSetZero(direction);

			max = IplImage.create(width, height, IPL_DEPTH_32F, 1);
			cvSetZero(max);

			temp32f = IplImage.create(width, height, IPL_DEPTH_32F, 1);
			mask = IplImage.create(width, height, IPL_DEPTH_8U, 1);
		}

		// get the best angle for each pixel
		// the best angle is the one with the greatest value after filtering
		// the filters are stored in the array filters
		{
			IplImage filtered = temp32f;
			for (int i = 0; i < filters.length; i++) {
				CvMat filter = filters[i];
				int sum = filterSums[i];

				cvFilter2D(input, filtered, filter, cvPoint(-1, -1));

				cvScale(filtered, filtered, 1.0 / sum, 0);

				cvCmp(max, filtered, mask, CV_CMP_LT);
				cvSet(direction, cvScalarAll(i), mask);
				cvMax(max, filtered, max);
			}

			// cvShowImage("max", max);
			// cvWaitKey();
		}

		// debug output
		// outputFilteredImages(img, direction, mask, temp32f);

		// Dilate angles to privilege better angles
		{
			IplImage temp = direction.clone();
			IplImage maxDil = max.clone();
			IplImage dil = temp32f;

			DilateProcessor processor = new DilateProcessor(dilSize);

			cvSetZero(direction);
			for (int i = 0; i < filters.length; i++) {
				cvCmpS(temp, i, mask, CV_CMP_EQ);
				cvSetZero(dil);
				cvSet(dil, CvScalar.ONE, mask);
				cvMul(max, dil, dil, 1);

				processor.process(dil, null);

				cvCmp(maxDil, dil, mask, CV_CMP_LE);
				cvSet(direction, cvScalarAll(i), mask);
				cvMax(maxDil, dil, maxDil);
			}

			//cvShowImage("max", maxDil);
			//cvWaitKey();
		}

		// debug output
		// outputDirections(img, direction, mask, temp32f);

		hists = new HashMap<Feature, double[]>();
		int[] hist = new int[filters.length];

		for (Feature feature : features) {
			CvMat subDirection;
			CvMat subMask;

			// create a mask of the feature
			cvSetZero(mask);
			feature.fill(mask, CvScalar.WHITE);

			// set sub-images of direction and mask
			{
				Vector2D fmin = feature.box().min();
				Vector2D fmax = feature.box().max();

				int xmin = (int) Math.min(Math.max(fmin.x, 0), img.width() - 1);
				int xmax = (int) Math.min(Math.max(fmax.x, 0), img.width() - 1);
				int ymin = (int) Math
						.min(Math.max(fmin.y, 0), img.height() - 1);
				int ymax = (int) Math
						.min(Math.max(fmin.y, 0), img.height() - 1);
				int width = xmax - xmin;
				int height = ymax - ymin;

				subDirection = CvMat.createHeader(height, width, CV_8UC1);
				subMask = CvMat.createHeader(height, width, CV_8UC1);

				CvRect rect = cvRect(xmin, ymin, width, height);

				cvGetSubRect(direction, subDirection, rect);
				cvGetSubRect(mask, subMask, rect);
			}

			// set histogram
			Arrays.fill(hist, 0);
			for (int i = 0; i < subDirection.rows(); i++) {
				for (int j = 0; j < subDirection.cols(); j++) {
					if (cvGetReal2D(subMask, i, j) != 0) {
						int val = (int) cvGetReal2D(subDirection, i, j);
						hist[val]++;
					}
				}
			}

			// calculate sum
			int sum = 0;
			for (int i = 0; i < hist.length; i++) {
				sum += hist[i];
			}

			// calculate percentages
			double[] percentages = new double[hist.length];
			for (int i = 0; i < hist.length; i++) {
				percentages[i] = hist[i] / (double) sum;
			}

			hists.put(feature, percentages);

			// System.out.println(Arrays.toString(ownHist));
			// cvShowImage("mask", mask);
			// cvWaitKey();
		}

		return super.linkFeatures(features, img);
	}

	@Override
	public boolean link(Feature f0, Feature f1) {
		double[] hist0 = hists.get(f0);
		double[] hist1 = hists.get(f1);

		double bestVal = 0;

		for (int i = 0; i < hist0.length; i++) {
			double val = Math.min(hist0[i], hist1[i]);
			if (bestVal < val) {
				bestVal = val;
			}
		}

		if (bestVal > 0.2) {
			
			return true;
		}

		return false;
	}

	private void outputFilteredImages(IplImage img, IplImage direction,
			IplImage mask, IplImage temp32f) {
		for (int i = 0; i < filters.length; i++) {
			cvCmpS(direction, i, mask, CV_CMP_EQ);
			cvAnd(mask, img, mask, null);
			cvSetZero(temp32f);
			cvSet(temp32f, CvScalar.ONE, mask);

			cvShowImage("map", temp32f);
			cvWaitKey();
		}
	}

	private void outputDirections(IplImage img, IplImage direction,
			IplImage mask, IplImage temp32f) {
		IplImage angle = temp32f;
		int size3 = 11;
		int border3 = 6;

		img = img.clone();

		// get angle-map
		cvSetZero(angle);
		for (int i = 0; i < angles.length; i++) {
			cvCmpS(direction, i, mask, CV_CMP_EQ);
			cvSet(angle, cvScalarAll(angles[i]), mask);
		}

		// average angles over the displayed area
		// not always correct (180� == 0�)
		cvSmooth(angle, angle, CV_BLUR, size3);

		// draw angles
		for (int y = border3; y < img.height() - border3; y += size3) {
			for (int x = border3; x < img.width() - border3; x += size3) {
				double a = cvGetReal2D(angle, y, x);
				CvPoint p0 = cvPoint(
						x - (int) Math.round(Math.cos(a) * border3), y
								- (int) Math.round(Math.sin(a) * border3));
				CvPoint p1 = cvPoint(
						x + (int) Math.round(Math.cos(a) * border3), y
								+ (int) Math.round(Math.sin(a) * border3));
				cvDrawLine(img, p0, p1, CvScalar.GRAY, 1, 0, 0);
			}
		}

		cvShowImage("img", img);
		cvWaitKey();
	}
}
