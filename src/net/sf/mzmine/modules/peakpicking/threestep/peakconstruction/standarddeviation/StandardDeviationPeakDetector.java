/*
 * Copyright 2006-2008 The MZmine Development Team
 * 
 * This file is part of MZmine.
 * 
 * MZmine is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * MZmine; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.modules.peakpicking.threestep.peakconstruction.standarddeviation;

import java.util.Vector;

import net.sf.mzmine.data.Peak;
import net.sf.mzmine.data.RawDataFile;
import net.sf.mzmine.modules.peakpicking.threestep.peakconstruction.ConnectedPeak;
import net.sf.mzmine.modules.peakpicking.threestep.peakconstruction.PeakBuilder;
import net.sf.mzmine.modules.peakpicking.threestep.xicconstruction.Chromatogram;
import net.sf.mzmine.modules.peakpicking.threestep.xicconstruction.ConnectedMzPeak;

/**
 * This class implements a simple peak builder. This takes all collected MzPeaks
 * in one chromatogram and try to find all possible peaks. This detection
 * follows the concept of baseline in a chromatogram to set a peak (threshold
 * level).
 * 
 */
public class StandardDeviationPeakDetector implements PeakBuilder {

	// private Logger logger = Logger.getLogger(this.getClass().getName());

	private float standardDeviationLevel, minimumPeakHeight,
			minimumPeakDuration;

	public StandardDeviationPeakDetector(
			StandardDeviationPeakDetectorParameters parameters) {

		minimumPeakHeight = (Float) parameters
				.getParameterValue(StandardDeviationPeakDetectorParameters.minimumPeakHeight);
		minimumPeakDuration = (Float) parameters
				.getParameterValue(StandardDeviationPeakDetectorParameters.minimumPeakDuration);
		standardDeviationLevel = (Float) parameters
				.getParameterValue(StandardDeviationPeakDetectorParameters.standardDeviationLevel);
	}

	/**
	 * @see net.sf.mzmine.modules.peakpicking.threestep.peakconstruction.PeakBuilder#addChromatogram(net.sf.mzmine.modules.peakpicking.threestep.xicconstruction.Chromatogram,
	 *      net.sf.mzmine.data.RawDataFile)
	 */
	public Peak[] addChromatogram(Chromatogram chromatogram,
			RawDataFile dataFile) {

		ConnectedMzPeak[] cMzPeaks = chromatogram.getConnectedMzPeaks();

		float standardDeviationlevelPeak;

		int[] scanNumbers = chromatogram.getDataFile().getScanNumbers(1);
		float[] chromatoIntensities = new float[scanNumbers.length];
		float sumIntensities = 0;

		for (int i = 0; i < scanNumbers.length; i++) {

			ConnectedMzPeak mzValue = chromatogram
					.getConnectedMzPeak(scanNumbers[i]);
			if (mzValue != null) {
				chromatoIntensities[i] = mzValue.getMzPeak().getIntensity();
			} else
				chromatoIntensities[i] = 0;
			sumIntensities += chromatoIntensities[i];
		}

		standardDeviationlevelPeak = calcChromatogramThreshold(
				chromatoIntensities, (sumIntensities / scanNumbers.length),
				standardDeviationLevel);

		Vector<ConnectedMzPeak> regionOfMzPeaks = new Vector<ConnectedMzPeak>();
		Vector<ConnectedPeak> underDetectionPeaks = new Vector<ConnectedPeak>();

		if (cMzPeaks.length > 0) {

			for (ConnectedMzPeak mzPeak : cMzPeaks) {

				if (mzPeak.getMzPeak().getIntensity() > standardDeviationlevelPeak) {
					regionOfMzPeaks.add(mzPeak);
				} else if (regionOfMzPeaks.size() != 0) {
					ConnectedPeak peak = new ConnectedPeak(dataFile,
							regionOfMzPeaks.get(0));
					for (int i = 0; i < regionOfMzPeaks.size(); i++) {
						peak.addMzPeak(regionOfMzPeaks.get(i));
					}
					regionOfMzPeaks.clear();

					float pLength = peak.getRawDataPointsRTRange().getSize();
					float pHeight = peak.getHeight();
					if ((pLength >= minimumPeakDuration)
							&& (pHeight >= minimumPeakHeight)) {
						underDetectionPeaks.add(peak);
					}

				}

			}

			if (regionOfMzPeaks.size() != 0) {
				ConnectedPeak peak = new ConnectedPeak(dataFile,
						regionOfMzPeaks.get(0));
				for (int i = 0; i < regionOfMzPeaks.size(); i++) {
					peak.addMzPeak(regionOfMzPeaks.get(i));
				}

				float pLength = peak.getRawDataPointsRTRange().getSize();
				float pHeight = peak.getHeight();
				if ((pLength >= minimumPeakDuration)
						&& (pHeight >= minimumPeakHeight)) {
					underDetectionPeaks.add(peak);
				}

			}

		}

		// logger.finest(" Numero de picos " + underDetectionPeaks.size());
		return underDetectionPeaks.toArray(new Peak[0]);
	}

	/**
	 * 
	 * @param chromatoIntensities
	 * @param avgIntensities
	 * @param chromatographicThresholdLevel
	 * @return
	 */
	private float calcChromatogramThreshold(float[] chromatoIntensities,
			float avgIntensities, float chromatographicThresholdLevel) {

		float standardDeviation = 0;
		float percentage = 1.0f - chromatographicThresholdLevel;

		for (int i = 0; i < chromatoIntensities.length; i++) {
			float deviation = chromatoIntensities[i] - avgIntensities;
			float deviation2 = deviation * deviation;
			standardDeviation += deviation2;
		}

		standardDeviation /= chromatoIntensities.length;
		standardDeviation = (float) Math.sqrt(standardDeviation);

		float avgDifference = 0;
		int cont = 0;

		for (int i = 0; i < chromatoIntensities.length; i++) {
			if (chromatoIntensities[i] < standardDeviation) {
				avgDifference += (standardDeviation - chromatoIntensities[i]);
				cont++;
			}
		}

		avgDifference /= cont;
		return standardDeviation - (avgDifference * percentage);
	}

}
