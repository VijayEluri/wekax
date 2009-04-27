/*
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program; if not, write to the Free Software
 *    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

/*
 *    SVMAttributeEval.java
 *    Copyright (C) 2002 Eibe Frank
 *    Mod by Kieran Holland
 *
 */

package weka.attributeSelection;

import java.io.*;
import java.util.*;
import weka.core.*;
import weka.classifiers.functions.SMO;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.MakeIndicator;
import weka.attributeSelection.*;

/** 
 * Class for Evaluating attributes individually by using the SVM
 * classifier. Attributes are ranked by the square of the weight
 * assigned by the SVM. Attribute selection for multiclass problems
 * is handled by ranking attributes for each class seperately
 * using a one-vs-all method and then "dealing" from the top of 
 * each pile to give a final ranking.<p>
 *
 * Valid options are: <p>
 *
 * -X <constant rate of elimination> <br>
 * Specify constant rate at which attributes are eliminated per invocation
 * of the support vector machine. Default = 1.<p>
 * 
 * -Y <percent rate of elimination> <br>
 * Specify the percentage rate at which attributes are eliminated per invocation
 * of the support vector machine. This setting trumps the constant rate setting. 
 * Default = 0 (percentage rate ignored).<p>
 *
 * -Z <threshold for percent elimination> <br>
 * Specify the threshold below which the percentage elimination method
 * reverts to the constant elimination method.<p>
 *
 * -C <complexity parameter> <br>
 * Specify the value of C - the complexity parameter to be passed on
 * to the support vector machine. <p>
 * 
 * -P <episilon> <br>
 * Sets the epsilon for round-off error. (default 1.0e-25)<p>
 *
 * -T <tolerance> <br>
 * Sets the tolerance parameter. (default 1.0e-10)<p>
 *
 * @author Eibe Frank (eibe@cs.waikato.ac.nz)
 * @author Mark Hall (mhall@cs.waikato.ac.nz)
 * @version $Revision: 1.1.1.1 $
 */
public class SVMAttributeEval extends AttributeEvaluator
  implements OptionHandler {

  /** The attribute scores */
  private double[] m_attScores;

  /** Constant rate of attribute elimination per iteration */
  private int m_numToEliminate = 1;

  /** Percentage rate of attribute elimination, trumps constant
      rate (above threshold), ignored if = 0  */
  private int m_percentToEliminate = 0;

  /** Threshold below which percent elimination switches to
      constant elimination */
  private int m_percentThreshold = 0;

  /** Complexity parameter to pass on to SMO */
  private double m_smoCParameter = 1.0;

  /** Tolerance parameter to pass on to SMO */
  private double m_smoTParameter = 1.0e-10;

  /** Epsilon parameter to pass on to SMO */
  private double m_smoPParameter = 1.0e-25;

  /** Filter parameter to pass on to SMO */
  private int m_smoFilterType = 0;

  /**
   * Returns a string describing this attribute evaluator
   * @return a description of the evaluator suitable for
   * displaying in the explorer/experimenter gui
   */
  public String globalInfo() {
    return "SVMAttributeEval :\n\nEvaluates the worth of an attribute by "
      + "using an SVM classifier.\n";
  }

  /**
   * Constructor
   */
  public SVMAttributeEval() {
    resetOptions();
  }

  /**
   * Returns an enumeration describing all the available options
   *
   * @return an enumeration of options
   */
  public Enumeration listOptions() {
    Vector newVector = new Vector(4);

    newVector.addElement(
			 new Option(
				    "\tSpecify the constant rate of attribute\n"
				    + "\telimination per invocation of\n"
				    + "\tthe support vector machine.\n"
				    + "\tDefault = 1.",
				    "X",
				    1,
				    "-X <constant rate of elimination>"));

    newVector.addElement(
			 new Option(
				    "\tSpecify the percentage rate of attributes to\n"
				    + "\telimination per invocation of\n"
				    + "\tthe support vector machine.\n"
				    + "\tTrumps constant rate (above threshold).\n"
				    + "\tDefault = 0.",
				    "Y",
				    1,
				    "-Y <percent rate of elimination>"));

    newVector.addElement(
			 new Option(
				    "\tSpecify the threshold below which \n"
				    + "\tpercentage attribute elimination\n"
				    + "\treverts to the constant method.\n",
				    "Z",
				    1,
				    "-Z <threshold for percent elimination>"));


    newVector.addElement(
			 new Option(
				    "\tSpecify the value of P (epsilon\n"
				    + "\tparameter) to pass on to the\n"
				    + "\tsupport vector machine.\n"
				    + "\tDefault = 1.0e-25",
				    "P",
				    1,
				    "-P <epsilon>"));

    newVector.addElement(
			 new Option(
				    "\tSpecify the value of T (tolerance\n"
				    + "\tparameter) to pass on to the\n"
				    + "\tsupport vector machine.\n"
				    + "\tDefault = 1.0e-10",
				    "T",
				    1,
				    "-T <tolerance>"));

    newVector.addElement(
			 new Option(
				    "\tSpecify the value of C (complexity\n"
				    + "\tparameter) to pass on to the\n"
				    + "\tsupport vector machine.\n"
				    + "\tDefault = 1.0",
				    "C",
				    1,
				    "-C <complexity>"));

    newVector.addElement(new Option("\tWhether the SVM should "
				    + "0=normalize/1=standardize/2=neither. "
				    + "(default 0=normalize)",
				    "N",
				    1,
				    "-N"));

    return newVector.elements();
  }

  /**
   * Parses a given list of options
   *
   * Valid options are: <p>
   *
   * -X <constant rate of elimination> <br>
   * Specify constant rate at which attributes are eliminated per invocation
   * of the support vector machine. Default = 1.<p>
   * 
   * -Y <percent rate of elimination> <br>
   * Specify the percentage rate at which attributes are eliminated per invocation
   * of the support vector machine. This setting trumps the constant rate setting. 
   * Default = 0 (percentage rate ignored).<p>
   *
   * -Z <threshold for percent elimination> <br>
   * Specify the threshold below which the percentage elimination method
   * reverts to the constant elimination method.<p>
   *
   * -C <complexity parameter> <br>
   * Specify the value of C - the complexity parameter to be passed on
   * to the support vector machine. <p>
   * 
   * -P <episilon> <br>
   * Sets the epsilon for round-off error. (default 1.0e-25)<p>
   *
   * -T <tolerance> <br>
   * Sets the tolerance parameter. (default 1.0e-10)<p>
   *
   * -N <0|1|2> <br>
   * Whether the SVM should 0=normalize/1=standardize/2=neither. (default 0=normalize)<p>
   *
   * @param options the list of options as an array of strings
   * @exception Exception if an error occurs
   */
  public void setOptions(String[] options) throws Exception {
    String optionString;

    optionString = Utils.getOption('X', options);
    if (optionString.length() != 0) {
      setAttsToEliminatePerIteration(Integer.parseInt(optionString));
    }

    optionString = Utils.getOption('Y', options);
    if (optionString.length() != 0) {
      setPercentToEliminatePerIteration(Integer.parseInt(optionString));
    }

    optionString = Utils.getOption('Z', options);
    if (optionString.length() != 0) {
      setPercentThreshold(Integer.parseInt(optionString));
    }

    optionString = Utils.getOption('P', options);
    if (optionString.length() != 0) {
      setEpsilonParameter((new Double(optionString)).doubleValue());
    }

    optionString = Utils.getOption('T', options);
    if (optionString.length() != 0) {
      setToleranceParameter((new Double(optionString)).doubleValue());
    }

    optionString = Utils.getOption('C', options);
    if (optionString.length() != 0) {
      setComplexityParameter((new Double(optionString)).doubleValue());
    }

    optionString = Utils.getOption('N', options);
    if (optionString.length() != 0) {
      setFilterType(new SelectedTag(Integer.parseInt(optionString), SMO.TAGS_FILTER));
    } else {
      setFilterType(new SelectedTag(SMO.FILTER_NORMALIZE, SMO.TAGS_FILTER));
    }

    Utils.checkForRemainingOptions(options);
  }

  /**
   * Gets the current settings of SVMAttributeEval
   *
   * @return an array of strings suitable for passing to setOptions() 
   */
  public String[] getOptions() {
    String[] options = new String[14];
    int current = 0;

    options[current++] = "-X";
    options[current++] = "" + getAttsToEliminatePerIteration();

    options[current++] = "-Y";
    options[current++] = "" + getPercentToEliminatePerIteration();

    options[current++] = "-Z";
    options[current++] = "" + getPercentThreshold();
		
    options[current++] = "-P";
    options[current++] = "" + getEpsilonParameter();

    options[current++] = "-T";
    options[current++] = "" + getToleranceParameter();		

    options[current++] = "-C";
    options[current++] = "" + getComplexityParameter();		

    options[current++] = "-N";
    options[current++] = "" + m_smoFilterType;		

    while (current < options.length) {
      options[current++] = "";
    }

    return options;
  }

  //________________________________________________________________________

  /**
   * Returns a tip text for this property suitable for display in the
   * GUI
   *
   * @return tip text string describing this property
   */
  public String attsToEliminatePerIterationTipText() {
    return "Constant rate of attribute elimination.";
  }

  /**
   * Returns a tip text for this property suitable for display in the
   * GUI
   *
   * @return tip text string describing this property
   */
  public String percentToEliminatePerIterationTipText() {
    return "Percent rate of attribute elimination.";
  }

  /**
   * Returns a tip text for this property suitable for display in the
   * GUI
   *
   * @return tip text string describing this property
   */
  public String percentThresholdTipText() {
    return "Threshold below which percent elimination reverts to constant elimination.";
  }

  /**
   * Returns a tip text for this property suitable for display in the
   * GUI
   *
   * @return tip text string describing this property
   */
  public String epsilonParameterTipText() {
    return "P epsilon parameter to pass to the SVM";
  }

  /**
   * Returns a tip text for this property suitable for display in the
   * GUI
   *
   * @return tip text string describing this property
   */
  public String toleranceParameterTipText() {
    return "T tolerance parameter to pass to the SVM";
  }

  /**
   * Returns a tip text for this property suitable for display in the
   * GUI
   *
   * @return tip text string describing this property
   */
  public String complexityParameterTipText() {
    return "C complexity parameter to pass to the SVM";
  }

  /**
   * Returns a tip text for this property suitable for display in the
   * GUI
   *
   * @return tip text string describing this property
   */
  public String filterTypeTipText() {
    return "filtering used by the SVM";
  }

  //________________________________________________________________________

  /**
   * Set the constant rate of attribute elimination per iteration
   *
   * @param X the constant rate of attribute elimination per iteration
   */
  public void setAttsToEliminatePerIteration(int cRate) {
    m_numToEliminate = cRate;
  }

  /**
   * Get the constant rate of attribute elimination per iteration
   *
   * @return the constant rate of attribute elimination per iteration
   */
  public int getAttsToEliminatePerIteration() {
    return m_numToEliminate;
  }

  /**
   * Set the percentage of attributes to eliminate per iteration
   *
   * @param Y percent of attributes to eliminate per iteration
   */
  public void setPercentToEliminatePerIteration(int pRate) {
    m_percentToEliminate = pRate;
  }

  /**
   * Get the percentage rate of attribute elimination per iteration
   *
   * @return the percentage rate of attribute elimination per iteration
   */
  public int getPercentToEliminatePerIteration() {
    return m_percentToEliminate;
  }

  /**
   * Set the threshold below which percentage elimination reverts to
   * constant elimination.
   *
   * @param thresh percent of attributes to eliminate per iteration
   */
  public void setPercentThreshold(int pThresh) {
    m_percentThreshold = pThresh;
  }

  /**
   * Get the threshold below which percentage elimination reverts to 
   * constant elimination.
   *
   * @return the threshold below which percentage elimination stops
   */
  public int getPercentThreshold() {
    return m_percentThreshold;
  }

  /**
   * Set the value of P for SMO
   *
   * @param svmP the value of P
   */
  public void setEpsilonParameter(double svmP) {
    m_smoPParameter = svmP;
  }

  /**
   * Get the value of P used with SMO
   *
   * @return the value of P
   */
  public double getEpsilonParameter() {
    return m_smoPParameter;
  }
	
  /**
   * Set the value of T for SMO
   *
   * @param svmC the value of T
   */
  public void setToleranceParameter(double svmT) {
    m_smoTParameter = svmT;
  }

  /**
   * Get the value of T used with SMO
   *
   * @return the value of T
   */
  public double getToleranceParameter() {
    return m_smoTParameter;
  }


  /**
   * Set the value of C for SMO
   *
   * @param svmC the value of C
   */
  public void setComplexityParameter(double svmC) {
    m_smoCParameter = svmC;
  }

  /**
   * Get the value of C used with SMO
   *
   * @return the value of C
   */
  public double getComplexityParameter() {
    return m_smoCParameter;
  }

  /**
   * The filtering mode to pass to SMO
   *
   * @param newType the new filtering mode
   */
  public void setFilterType(SelectedTag newType) {
    
    if (newType.getTags() == SMO.TAGS_FILTER) {
      m_smoFilterType = newType.getSelectedTag().getID();
    }
  }

  /**
   * Get the filtering mode passed to SMO
   *
   * @return the filtering mode
   */
  public SelectedTag getFilterType() {

    return new SelectedTag(m_smoFilterType, SMO.TAGS_FILTER);
  }

  //________________________________________________________________________

  /**
   * Initializes the evaluator.
   *
   * @param data set of instances serving as training data 
   * @exception Exception if the evaluator has not been 
   * generated successfully
   */
  public void buildEvaluator(Instances data) throws Exception {
    if (data.checkForStringAttributes()) {
      throw new Exception("Can't handle string attributes!");
    }
    if (!data.classAttribute().isNominal()) {
      throw new Exception("Class must be nominal!");
    }
    for (int i = 0; i < data.numAttributes(); i++) {
      if (data.attribute(i).isNominal() && (data.attribute(i).numValues() != 2) && !(i==data.classIndex()) ) {
	throw new Exception("All nominal attributes must be binary!");
      }
    }
    System.out.println("Class attribute: " + data.attribute(data.classIndex()).name());
    // Check settings		
    m_numToEliminate = (m_numToEliminate > 1) ? m_numToEliminate : 1;
    m_percentToEliminate = (m_percentToEliminate < 100) ? m_percentToEliminate : 100;
    m_percentToEliminate = (m_percentToEliminate > 0) ? m_percentToEliminate : 0;
    m_percentThreshold = (m_percentThreshold < data.numAttributes()) ? m_percentThreshold : data.numAttributes() - 1;
    m_percentThreshold = (m_percentThreshold > 0) ? m_percentThreshold : 0;

    // Get ranked attributes for each class seperately, one-vs-all
    int[][] attScoresByClass;
    int numAttr = data.numAttributes() - 1;
    if(data.numClasses()>2) {
      attScoresByClass = new int[data.numClasses()][numAttr];
      for (int i = 0; i < data.numClasses(); i++) {
	attScoresByClass[i] = rankBySVM(i, data);
      }
    }
    else {
      attScoresByClass = new int[1][numAttr];
      attScoresByClass[0] = rankBySVM(0, data);
    }

    // Cycle through class-specific ranked lists, poping top one off for each class
    // and adding it to the overall ranked attribute list if it's not there already
    ArrayList ordered = new ArrayList(numAttr);
    for (int i = 0; i < numAttr; i++) {
      for (int j = 0; j < (data.numClasses()>2 ? data.numClasses() : 1); j++) {
	Integer rank = new Integer(attScoresByClass[j][i]);
	if (!ordered.contains(rank))
	  ordered.add(rank);
      }
    }
    m_attScores = new double[data.numAttributes()];
    Iterator listIt = ordered.iterator();
    for (double i = (double) numAttr; listIt.hasNext(); i = i - 1.0) {
      m_attScores[((Integer) listIt.next()).intValue()] = i;
    }
  }

  /**
   * Get SVM-ranked attribute indexes (best to worst) selected for
   * the class attribute indexed by classInd (one-vs-all).
   */
  private int[] rankBySVM(int classInd, Instances data) {
    // Holds a mapping into the original array of attribute indices
    int[] origIndices = new int[data.numAttributes()];
    for (int i = 0; i < origIndices.length; i++)
      origIndices[i] = i;
    
    // Count down of number of attributes remaining
    int numAttrLeft = data.numAttributes()-1;
    // Ranked attribute indices for this class, one vs.all (highest->lowest)
    int[] attRanks = new int[numAttrLeft];

    try {
      MakeIndicator filter = new MakeIndicator();
      filter.setAttributeIndex(data.classIndex());
      filter.setNumeric(false);
      filter.setValueIndex(classInd);
      filter.setInputFormat(data);
      Instances trainCopy = Filter.useFilter(data, filter);
      double pctToElim = ((double) m_percentToEliminate) / 100.0;
      while (numAttrLeft > 0) {
	int numToElim;
	if (pctToElim > 0) {
	  numToElim = (int) (trainCopy.numAttributes() * pctToElim);
	  numToElim = (numToElim > 1) ? numToElim : 1;
	  if (numAttrLeft - numToElim <= m_percentThreshold) {
	    pctToElim = 0;
	    numToElim = numAttrLeft - m_percentThreshold;
	  }
	} else {
	  numToElim = (numAttrLeft >= m_numToEliminate) ? m_numToEliminate : numAttrLeft;
	}
	
	// Build the linear SVM with default parameters
	SMO smo = new SMO();
				
	// SMO seems to get stuck if data not normalised when few attributes remain
	// smo.setNormalizeData(numAttrLeft < 40);
	smo.setFilterType(new SelectedTag(m_smoFilterType, SMO.TAGS_FILTER));
	smo.setEpsilon(m_smoPParameter);
	smo.setToleranceParameter(m_smoTParameter);
	smo.setC(m_smoCParameter);
	smo.buildClassifier(trainCopy);
				
	// Find the attribute with maximum weight^2
	FastVector weightsAndIndices = smo.weights();
	double[] weightsSparse = (double[]) weightsAndIndices.elementAt(0);
	int[] indicesSparse = (int[]) weightsAndIndices.elementAt(1);
	double[] weights = new double[trainCopy.numAttributes()];
	for (int j = 0; j < weightsSparse.length; j++) {
	  weights[indicesSparse[j]] = weightsSparse[j] * weightsSparse[j];
	}
	weights[trainCopy.classIndex()] = Double.MAX_VALUE;
	int minWeightIndex;
	int[] featArray = new int[numToElim];
	boolean[] eliminated = new boolean[origIndices.length];
	for (int j = 0; j < numToElim; j++) {
	  minWeightIndex = Utils.minIndex(weights);
	  attRanks[--numAttrLeft] = origIndices[minWeightIndex];
	  featArray[j] = minWeightIndex;
	  eliminated[minWeightIndex] = true;
	  weights[minWeightIndex] = Double.MAX_VALUE;
	}
				
	// Delete the worst attributes. 
	weka.filters.unsupervised.attribute.Remove delTransform =
	  new weka.filters.unsupervised.attribute.Remove();
	delTransform.setInvertSelection(false);
	delTransform.setAttributeIndicesArray(featArray);
	delTransform.setInputFormat(trainCopy);
	trainCopy = Filter.useFilter(trainCopy, delTransform);
				
	// Update the array of remaining attribute indices
	int[] temp = new int[origIndices.length - numToElim];
	int k = 0;
	for (int j = 0; j < origIndices.length; j++) {
	  if (!eliminated[j]) {
	    temp[k++] = origIndices[j];
	  }
	}
	origIndices = temp;
      }			
      // Carefully handle all exceptions
    } catch (Exception e) {
      e.printStackTrace();			
    }
    return attRanks;
  }

  /**
   * Resets options to defaults.
   */
  protected void resetOptions() {
    m_attScores = null;
  }

  /**
   * Evaluates an attribute by returning the rank of the square of its coefficient in a
   * linear support vector machine.
   *
   *@param attribute the index of the attribute to be evaluated
   * @exception Exception if the attribute could not be evaluated
   */
  public double evaluateAttribute(int attribute) throws Exception {
    return m_attScores[attribute];
  }

  /**
   * Return a description of the evaluator
   * @return description as a string
   */
  public String toString() {

    StringBuffer text = new StringBuffer();
    if (m_attScores == null) {
      text.append("\tSVM feature evaluator has not been built yet");
    } else {
      text.append("\tSVM feature evaluator");
    }

    text.append("\n");
    return text.toString();
  }

  /**
   * Main method for testing this class.
   *
   * @param args the options
   */
  public static void main(String[] args) {
    try {
      System.out.println(AttributeSelection.SelectAttributes(new SVMAttributeEval(), args));
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println(e.getMessage());
    }
  }
}