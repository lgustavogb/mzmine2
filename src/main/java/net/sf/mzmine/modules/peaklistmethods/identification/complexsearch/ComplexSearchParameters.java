/*
 * Copyright 2006-2014 The MZmine 2 Development Team
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

package net.sf.mzmine.modules.peaklistmethods.identification.complexsearch;

import net.sf.mzmine.data.IonizationType;
import net.sf.mzmine.parameters.Parameter;
import net.sf.mzmine.parameters.impl.SimpleParameterSet;
import net.sf.mzmine.parameters.parametertypes.ComboParameter;
import net.sf.mzmine.parameters.parametertypes.MZToleranceParameter;
import net.sf.mzmine.parameters.parametertypes.PeakListsParameter;
import net.sf.mzmine.parameters.parametertypes.PercentParameter;
import net.sf.mzmine.parameters.parametertypes.RTToleranceParameter;

public class ComplexSearchParameters extends SimpleParameterSet {

    public static final PeakListsParameter peakLists = new PeakListsParameter();

    public static final ComboParameter<IonizationType> ionizationMethod = new ComboParameter<IonizationType>(
            "Ionization method",
            "Type of ion used to calculate the neutral mass",
            IonizationType.values());

    public static final RTToleranceParameter rtTolerance = new RTToleranceParameter();

    public static final MZToleranceParameter mzTolerance = new MZToleranceParameter();

    public static final PercentParameter maxComplexHeight = new PercentParameter(
            "Max complex peak height",
            "Maximum height of the recognized complex peak, relative to the highest of component peaks");

    public ComplexSearchParameters() {
        super(new Parameter[] { peakLists, ionizationMethod, rtTolerance,
                mzTolerance, maxComplexHeight });
    }

}
