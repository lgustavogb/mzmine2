/*
 * Copyright 2006-2011 The MZmine 2 Development Team
 *
 * This file is part of MZmine 2.
 *
 * MZmine 2 is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * MZmine 2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * MZmine 2; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 */
package net.sf.mzmine.modules.peaklistmethods.dataanalysis.heatmaps;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import net.sf.mzmine.data.ChromatographicPeak;
import net.sf.mzmine.data.PeakList;
import net.sf.mzmine.data.PeakListRow;
import net.sf.mzmine.data.RawDataFile;
import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.modules.rawdatamethods.filtering.baselinecorrection.RUtilities;
import net.sf.mzmine.parameters.ParameterSet;
import net.sf.mzmine.parameters.UserParameter;
import net.sf.mzmine.project.MZmineProject;
import net.sf.mzmine.taskcontrol.AbstractTask;
import net.sf.mzmine.taskcontrol.TaskStatus;
import org.apache.commons.math.MathException;
import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math.stat.inference.TTestImpl;
import org.rosuda.JRI.Rengine;

public class HeatMapTask extends AbstractTask {

        private String outputType;
        private boolean log, rcontrol, scale, plegend, area, onlyIdentified;
        private int height, width, columnMargin, rowMargin, starSize;
        private File outputFile;
        private double[][] newPeakList;
        private String[] rowNames, colNames;
        private String[][] pValueMatrix;
        private double finishedPercentage = 0.0f;

        public HeatMapTask(PeakList peakList, ParameterSet parameters) {

                outputFile = parameters.getParameter(HeatMapParameters.fileName).getValue();
                outputType = parameters.getParameter(HeatMapParameters.fileTypeSelection).getValue();
                ParameterType parameterName = parameters.getParameter(HeatMapParameters.selectionData).getValue();
                String referenceGroup = parameters.getParameter(HeatMapParameters.referenceGroup).getValue();
                area = parameters.getParameter(HeatMapParameters.usePeakArea).getValue();
                onlyIdentified = parameters.getParameter(HeatMapParameters.useIdenfiedRows).getValue();

                log = parameters.getParameter(HeatMapParameters.log).getValue();
                scale = parameters.getParameter(HeatMapParameters.scale).getValue();
                rcontrol = parameters.getParameter(HeatMapParameters.showControlSamples).getValue();
                plegend = parameters.getParameter(HeatMapParameters.plegend).getValue();

                height = parameters.getParameter(HeatMapParameters.height).getInt();
                width = parameters.getParameter(HeatMapParameters.width).getInt();
                columnMargin = parameters.getParameter(HeatMapParameters.columnMargin).getInt();
                rowMargin = parameters.getParameter(HeatMapParameters.rowMargin).getInt();
                starSize = parameters.getParameter(HeatMapParameters.star).getInt();

                if (parameterName != null) {
                        if (plegend) {
                                newPeakList = groupingDataset(peakList, parameterName, referenceGroup);
                        } else {
                                newPeakList = modifySimpleDataset(peakList, parameterName, referenceGroup);
                        }

                        if (newPeakList.length == 0 || newPeakList[0].length == 0) {
                                errorMessage = "The data for heat map is empty.";
                                setStatus(TaskStatus.ERROR);
                        }
                }

        }

        public String getTaskDescription() {
                return "Heat map... ";
        }

        public double getFinishedPercentage() {
                return finishedPercentage;
        }

        public void cancel() {
                setStatus(TaskStatus.CANCELED);
        }

        public void run() {
                try {
                        setStatus(TaskStatus.PROCESSING);
                        final Rengine rEngine;
                        try {
                                rEngine = RUtilities.getREngine();
                        } catch (Throwable t) {

                                throw new IllegalStateException(
                                        "Heat map requires R but it couldn't be loaded (" + t.getMessage() + ')');
                        }
                        this.finishedPercentage = 0.1f;

                        synchronized (RUtilities.R_SEMAPHORE) {

                                // Load gplots library
                                if (rEngine.eval("require(gplots)").asBool().isFALSE()) {

                                        throw new IllegalStateException("The \"gplots\" R package couldn't be loaded - is it installed in R?");
                                }

                                try {

                                        if (outputFile.equals("png")) {
                                                if ((height / columnMargin) < 5) {
                                                        errorMessage = "Figure margins too large.";
                                                        setStatus(TaskStatus.ERROR);
                                                }
                                        }

                                        rEngine.eval("dataset<- matrix(\"\",nrow =" + newPeakList[0].length + ",ncol=" + newPeakList.length + ")");

                                        if (plegend) {
                                                rEngine.eval("stars<- matrix(\"\",nrow =" + newPeakList[0].length + ",ncol=" + newPeakList.length + ")");
                                        }

                                        // assing the values to the matrix
                                        for (int row = 0; row < newPeakList[0].length; row++) {

                                                for (int column = 0; column < newPeakList.length; column++) {

                                                        int r = row + 1;
                                                        int c = column + 1;

                                                        double value = newPeakList[column][row];

                                                        if (plegend) {
                                                                String pValue = pValueMatrix[column][row];
                                                                rEngine.eval("stars[" + r + "," + c + "] = \"" + pValue + "\"");
                                                        }

                                                        if (!Double.isInfinite(value) && !Double.isNaN(value)) {

                                                                rEngine.eval("dataset[" + r + "," + c + "] = " + value);
                                                        } else {

                                                                rEngine.eval("dataset[" + r + "," + c + "] = NA");
                                                        }
                                                }
                                        }
                                        this.finishedPercentage = 0.2f;

                                        rEngine.eval("dataset <- apply(dataset, 2, as.numeric)");

                                        // Assign row names to the data set
                                        long rows = rEngine.rniPutStringArray(rowNames);
                                        rEngine.rniAssign("rowNames", rows, 0);
                                        rEngine.eval("rownames(dataset)<-rowNames");

                                        // Assign column names to the data set
                                        long columns = rEngine.rniPutStringArray(colNames);
                                        rEngine.rniAssign("colNames", columns, 0);
                                        rEngine.eval("colnames(dataset)<-colNames");

                                        this.finishedPercentage = 0.3f;

                                        // Remove the rows with too many NA's. The distances between rows can't be calculated if the rows don't have
                                        // at least one sample in common.
                                        rEngine.eval(" d <- as.matrix(dist(dataset))");
                                        rEngine.eval("d[upper.tri(d)] <- 0");
                                        rEngine.eval("dataset <- dataset[-na.action(na.omit(d)),]");

                                        this.finishedPercentage = 0.7f;

                                        String marginParameter = "margins = c(" + columnMargin + "," + rowMargin + ")";
                                        rEngine.eval("br<-c(seq(from=min(dataset,na.rm=T),to=0,length.out=256),seq(from=0,to=max(dataset,na.rm=T),length.out=256))", false);

                                        // Possible output file types
                                        if (outputType.contains("pdf")) {

                                                rEngine.eval("pdf(\"" + outputFile + "\", height=" + height + ", width=" + width + ")");
                                        } else if (outputType.contains("fig")) {

                                                rEngine.eval("xfig(\"" + outputFile + "\", height=" + height + ", width=" + width + ", horizontal = FALSE, pointsize = 12)");
                                        } else if (outputType.contains("svg")) {

                                                // Load RSvgDevice library
                                                if (rEngine.eval("require(RSvgDevice)").asBool().isFALSE()) {

                                                        throw new IllegalStateException("The \"RSvgDevice\" R package couldn't be loaded - is it installed in R?");
                                                }

                                                rEngine.eval("devSVG(\"" + outputFile + "\", height=" + height + ", width=" + width + ")");
                                        } else if (outputType.contains("png")) {

                                                rEngine.eval("png(\"" + outputFile + "\", height=" + height + ", width=" + width + ")");
                                        }

                                        if (plegend) {

                                                rEngine.eval("heatmap.2(dataset," + marginParameter + ", trace=\"none\", col=bluered(length(br)-1), breaks=br, cellnote=stars, notecol=\"black\", notecex=" + starSize + ", na.color=\"grey\")", false);
                                        } else {

                                                rEngine.eval("heatmap.2(dataset," + marginParameter + ", trace=\"none\", col=bluered(length(br)-1), breaks=br, na.color=\"grey\")", false);
                                        }

                                        rEngine.eval("dev.off()", false);
                                        this.finishedPercentage = 1.0f;
                                        
                                } catch (Throwable t) {

                                        throw new IllegalStateException("R error during the heat map creation", t);
                                }
                        }

                        setStatus(TaskStatus.FINISHED);

                } catch (Exception e) {
                        setStatus(TaskStatus.ERROR);

                        errorMessage = e.toString();
                        return;
                }
        }

        private double[][] modifySimpleDataset(PeakList peakList, ParameterType parameterName, String referenceGroup) {

                // Collect all data files
                Vector<RawDataFile> allDataFiles = new Vector<RawDataFile>();


                for (RawDataFile dataFile : peakList.getRawDataFiles()) {

                        // Each data file can only have one column in aligned peak list
                        if (allDataFiles.contains(dataFile)) {

                                setStatus(TaskStatus.ERROR);
                                errorMessage = "Cannot run alignment, because file "
                                        + dataFile + " is present in multiple peak lists";
                                return null;
                        }

                        allDataFiles.add(dataFile);
                }


                // Determine the reference group and non reference group (the rest of the samples) for raw data files
                List<RawDataFile> referenceDataFiles = new ArrayList<RawDataFile>();
                List<RawDataFile> nonReferenceDataFiles = new ArrayList<RawDataFile>();

                if (parameterName.isByParameter()) {

                        MZmineProject project = MZmineCore.getCurrentProject();
                        UserParameter selectedParameter = parameterName.getParameter();
                        for (RawDataFile rawDataFile : allDataFiles) {

                                Object paramValue = project.getParameterValue(
                                        selectedParameter, rawDataFile);

                                if (paramValue.equals(referenceGroup)) {

                                        referenceDataFiles.add(rawDataFile);
                                } else {

                                        nonReferenceDataFiles.add(rawDataFile);
                                }
                        }
                }

                int numRows = 0;
                for (int row = 0; row < peakList.getNumberOfRows(); row++) {

                        if (!onlyIdentified || (onlyIdentified && peakList.getRow(row).getPeakIdentities().length > 0)) {
                                numRows++;
                        }
                }


                // Create a new aligned peak list with all the samples if the reference group has to be shown or with only
                // the non reference group if not.
                double[][] dataMatrix;
                if (rcontrol) {
                        dataMatrix = new double[allDataFiles.size()][numRows];
                } else {
                        dataMatrix = new double[nonReferenceDataFiles.size()][numRows];
                }


                // Data files that should be in the heat map
                List<RawDataFile> shownDataFiles = null;
                if (rcontrol) {
                        shownDataFiles = allDataFiles;
                } else {
                        shownDataFiles = nonReferenceDataFiles;
                }

                for (int row = 0; row < peakList.getNumberOfRows(); row++) {
                        PeakListRow rowPeak = peakList.getRow(row);
                        if (!onlyIdentified || (onlyIdentified && rowPeak.getPeakIdentities().length > 0)) {
                                // Average area or height of the reference group
                                double referenceAverage = 0;
                                int referencePeakCount = 0;
                                for (int column = 0; column < referenceDataFiles.size(); column++) {

                                        if (rowPeak.getPeak(referenceDataFiles.get(column)) != null) {

                                                if (area) {

                                                        referenceAverage += rowPeak.getPeak(referenceDataFiles.get(column)).getArea();
                                                } else {

                                                        referenceAverage += rowPeak.getPeak(referenceDataFiles.get(column)).getHeight();
                                                }
                                                referencePeakCount++;
                                        }
                                }
                                if (referencePeakCount > 0) {

                                        referenceAverage /= referencePeakCount;
                                }

                                // Divide the area or height of each peak by the average of the area or height of the reference peaks in each row
                                for (int column = 0; column < shownDataFiles.size(); column++) {
                                        double value = Double.NaN;
                                        if (rowPeak.getPeak(shownDataFiles.get(column)) != null) {

                                                ChromatographicPeak peak = rowPeak.getPeak(shownDataFiles.get(column));
                                                if (area) {

                                                        value = peak.getArea() / referenceAverage;
                                                } else {

                                                        value = peak.getHeight() / referenceAverage;
                                                }
                                                if (log) {

                                                        value = Math.log(value);
                                                }
                                        }

                                        dataMatrix[column][row] = value;
                                }
                        }
                }

                // Scale the data dividing the peak area/height by the standard deviation of each column
                if (scale) {
                        scale(dataMatrix);
                }

                // Create two arrays: row and column names
                rowNames = new String[dataMatrix[0].length];
                colNames = new String[shownDataFiles.size()];

                for (int column = 0; column < shownDataFiles.size(); column++) {

                        colNames[column] = shownDataFiles.get(column).getName();
                }
                for (int row = 0; row < peakList.getNumberOfRows(); row++) {
                        if (!onlyIdentified || (onlyIdentified && peakList.getRow(row).getPeakIdentities().length > 0)) {
                                if (peakList.getRow(row).getPeakIdentities() != null && peakList.getRow(row).getPeakIdentities().length > 0) {

                                        rowNames[row] = peakList.getRow(row).getPeakIdentities()[0].getName();
                                } else {

                                        rowNames[row] = "Unknown";
                                }
                        }
                }


                return dataMatrix;
        }

        private void scale(double[][] peakList) {
                DescriptiveStatistics stdDevStats = new DescriptiveStatistics();

                for (int columns = 0; columns < peakList.length; columns++) {
                        stdDevStats.clear();
                        for (int row = 0; row < peakList[columns].length; row++) {
                                if (!Double.isInfinite(peakList[columns][row]) && !Double.isNaN(peakList[columns][row])) {
                                        stdDevStats.addValue(peakList[columns][row]);
                                }
                        }

                        double stdDev = stdDevStats.getStandardDeviation();


                        for (int row = 0; row < peakList[columns].length; row++) {
                                if (stdDev != 0) {
                                        peakList[columns][row] = peakList[columns][row] / stdDev;
                                }
                        }
                }
        }

        private double[][] groupingDataset(PeakList peakList, ParameterType parameterName, String referenceGroup) {
                // Collect all data files
                Vector<RawDataFile> allDataFiles = new Vector<RawDataFile>();
                DescriptiveStatistics meanControlStats = new DescriptiveStatistics();
                DescriptiveStatistics meanGroupStats = new DescriptiveStatistics();

                for (RawDataFile dataFile : peakList.getRawDataFiles()) {

                        // Each data file can only have one column in aligned peak list
                        if (allDataFiles.contains(dataFile)) {

                                setStatus(TaskStatus.ERROR);
                                errorMessage = "Cannot run alignment, because file "
                                        + dataFile + " is present in multiple peak lists";
                                return null;
                        }

                        allDataFiles.add(dataFile);
                }

                // Determine the reference group and non reference group (the rest of the samples) for raw data files
                List<RawDataFile> referenceDataFiles = new ArrayList<RawDataFile>();
                List<RawDataFile> nonReferenceDataFiles = new ArrayList<RawDataFile>();

                List<String> groups = new ArrayList<String>();
                MZmineProject project = MZmineCore.getCurrentProject();
                UserParameter selectedParameter = parameterName.getParameter();
                if (parameterName.isByParameter()) {

                        for (RawDataFile rawDataFile : allDataFiles) {

                                Object paramValue = project.getParameterValue(
                                        selectedParameter, rawDataFile);

                                if (!groups.contains((String) paramValue)) {
                                        groups.add((String) paramValue);
                                }
                                if (paramValue.equals(referenceGroup)) {

                                        referenceDataFiles.add(rawDataFile);
                                } else {

                                        nonReferenceDataFiles.add(rawDataFile);
                                }
                        }
                }


                int numRows = 0;
                for (int row = 0; row < peakList.getNumberOfRows(); row++) {

                        if (!onlyIdentified || (onlyIdentified && peakList.getRow(row).getPeakIdentities().length > 0)) {
                                numRows++;
                        }
                }

                // Create a new aligned peak list with all the samples if the reference group has to be shown or with only
                // the non reference group if not.
                double[][] dataMatrix = new double[groups.size() - 1][numRows];
                pValueMatrix = new String[groups.size() - 1][numRows];


                // data files that should be in the heat map
                List<RawDataFile> shownDataFiles = nonReferenceDataFiles;


                for (int row = 0; row < peakList.getNumberOfRows(); row++) {
                        PeakListRow rowPeak = peakList.getRow(row);
                        if (!onlyIdentified || (onlyIdentified && rowPeak.getPeakIdentities().length > 0)) {
                                // Average area or height of the reference group
                                meanControlStats.clear();
                                for (int column = 0; column < referenceDataFiles.size(); column++) {

                                        if (rowPeak.getPeak(referenceDataFiles.get(column)) != null) {

                                                if (area) {

                                                        meanControlStats.addValue(rowPeak.getPeak(referenceDataFiles.get(column)).getArea());
                                                } else {

                                                        meanControlStats.addValue(rowPeak.getPeak(referenceDataFiles.get(column)).getHeight());
                                                }

                                        }
                                }


                                // Divide the area or height of each peak by the average of the area or height of the reference peaks in each row
                                int columnIndex = 0;
                                for (int column = 0; column < groups.size(); column++) {
                                        String group = groups.get(column);
                                        meanGroupStats.clear();
                                        if (!group.equals(referenceGroup)) {

                                                for (int dataColumn = 0; dataColumn < shownDataFiles.size(); dataColumn++) {

                                                        Object paramValue = project.getParameterValue(
                                                                selectedParameter, shownDataFiles.get(dataColumn));
                                                        if (rowPeak.getPeak(shownDataFiles.get(dataColumn)) != null && paramValue.equals(group)) {

                                                                ChromatographicPeak peak = rowPeak.getPeak(shownDataFiles.get(dataColumn));

                                                                if (!Double.isInfinite(peak.getArea()) && !Double.isNaN(peak.getArea())) {

                                                                        if (area) {

                                                                                meanGroupStats.addValue(peak.getArea());
                                                                        } else {

                                                                                meanGroupStats.addValue(peak.getHeight());
                                                                        }
                                                                }

                                                        }
                                                }


                                                double value = meanGroupStats.getMean() / meanControlStats.getMean();
                                                if (meanGroupStats.getN() > 1 && meanControlStats.getN() > 1) {
                                                        pValueMatrix[columnIndex][row] = this.getPvalue(meanGroupStats, meanControlStats);
                                                } else {
                                                        pValueMatrix[columnIndex][row] = "";
                                                }

                                                if (log) {

                                                        value = Math.log(value);
                                                }
                                                dataMatrix[columnIndex++][row] = value;
                                        }
                                }
                        }
                }

                // Scale the data dividing the peak area/height by the standard deviation of each column
                if (scale) {
                        scale(dataMatrix);
                }

                // Create two arrays: row and column names
                rowNames = new String[dataMatrix[0].length];
                colNames = new String[groups.size() - 1];

                int columnIndex = 0;
                for (String group : groups) {

                        if (!group.equals(referenceGroup)) {

                                colNames[columnIndex++] = group;
                        }
                }
                for (int row = 0; row < peakList.getNumberOfRows(); row++) {
                        if (!onlyIdentified || (onlyIdentified && peakList.getRow(row).getPeakIdentities().length > 0)) {
                                if (peakList.getRow(row).getPeakIdentities() != null && peakList.getRow(row).getPeakIdentities().length > 0) {

                                        rowNames[row] = peakList.getRow(row).getPeakIdentities()[0].getName();
                                } else {

                                        rowNames[row] = "Unknown";
                                }
                        }
                }

                return dataMatrix;
        }

        private String getPvalue(DescriptiveStatistics group1, DescriptiveStatistics group2) {
                TTestImpl ttest = new TTestImpl();
                String sig = "";
                try {
                        double pValue = ttest.tTest(group1, group2);
                        if (pValue < 0.05) {
                                sig = "*";
                        }
                        if (pValue < 0.01) {
                                sig = "**";
                        }
                        if (pValue < 0.001) {
                                sig = "***";
                        }

                } catch (IllegalArgumentException ex) {
                        sig = "-";

                } catch (MathException ex) {
                        sig = "-";
                }
                return sig;
        }
}
