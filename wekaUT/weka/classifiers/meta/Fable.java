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
 *    Fable.java
 *    Copyright (C) 2003 Prem Melville
 *
 */

//!! WARNING: Under Development !!

package weka.classifiers.meta;
import weka.classifiers.*;
import java.util.*;
import weka.core.*;
import weka.experiment.*;

/**
 * FABLE is a version of DECORATE that allows for active feature
 * acquisition.
 *
 * DECORATE is a meta-learner for building diverse ensembles of
 * classifiers by adding specially constructed artificial training
 * examples. Comprehensive experiments have demonstrated that this
 * technique is consistently more accurate than bagging and more
 * accurate that boosting when training data is limited. For more
 * details see <p>
 *
 * Prem Melville and Raymond J. Mooney. <i>Constructing diverse
 * classifier ensembles using artificial training examples.</i>
 * Proceedings of the Seventeeth International Joint Conference on
 * Artificial Intelligence 2003.<BR><BR>
 *
 * Valid options are:<p>
 *
 * -D <br>
 * Turn on debugging output.<p>
 *
 * -W classname <br>
 * Specify the full class name of a weak classifier as the basis for 
 * Decorate (default weka.classifiers.trees.j48.J48()).<p>
 *
 * -I num <br>
 * Specify the desired size of the committee (default 15). <p>
 *
 * -M iterations <br>
 * Set the maximum number of Decorate iterations (default 50). <p>
 *
 * -S seed <br>
 * Seed for random number generator. (default 0).<p>
 *
 * -R factor <br>
 * Factor that determines number of artificial examples to generate. <p>
 *
 * Options after -- are passed to the designated classifier.<p>
 *
 * @author Prem Melville (melville@cs.utexas.edu)
 */
public class Fable extends DistributionClassifier implements OptionHandler, ActiveFeatureAcquirer, ActiveLearner{

    //Alternate select committee (for control experiments)
    protected DistributionClassifier m_SelectionCommittee = null;

    /** Smoothing parameter for 0-values in distributions */
    protected double m_Epsilon = 0.000001;
    
    /** Set to true to get debugging output. */
    protected boolean m_Debug = true;

    /** The model base classifier to use. */
    protected Classifier m_Classifier = new weka.classifiers.trees.j48.J48();
      
    /** Vector of classifiers that make up the committee/ensemble. */
    protected Vector m_Committee = null;
    
    /** The desired ensemble size. */
    protected int m_DesiredSize = 15;

    /** The maximum number of Decorate iterations to run. */
    protected int m_NumIterations = 50;
    
    /** The seed for random number generation. */
    protected int m_Seed = 0;
    
    /** Amount of artificial/random instances to use - specified as a
        fraction of the training data size. */
    //protected double m_ArtSize = 1.0 ;
    protected double m_ArtSize = 0.5 ;

    /** The random number generator. */
    protected Random m_Random = new Random(0);
    
    /** Attribute statistics - used for generating artificial examples. */
    protected Vector m_AttributeStats = null;

    /** Enumeration of selective sampling schemes */
    static final int LABEL_MARGIN = 0,
	RANDOM = 1,
	ABS_LABEL_MARGIN = 2,
	UNLABELED_MARGIN = 3,
	ENTROPY = 4;
    
    
    static final int MAJORITY = 0,
	EUCLIDEAN = 1,
	JENSEN_SHANNON = 2,
	MARGIN = 3, 
	BAGGING = 4, BOOSTING = 5;

    /** The selective sampling scheme to use. **/
    protected int m_SelectionScheme = LABEL_MARGIN;
    
    /**
     * Returns an enumeration describing the available options
     *
     * @return an enumeration of all the available options
     */
    public Enumeration listOptions() {
	Vector newVector = new Vector(8);
	newVector.addElement(new Option(
	      "\tTurn on debugging output.",
	      "D", 0, "-D"));
	newVector.addElement(new Option(
              "\tDesired size of ensemble.\n" 
              + "\t(default 15)",
              "I", 1, "-I"));
	newVector.addElement(new Option(
	      "\tMaximum number of Decorate iterations.\n"
	      + "\t(default 50)",
	      "M", 1, "-M"));
	newVector.addElement(new Option(
	      "\tFull name of base classifier.\n"
	      + "\t(default weka.classifiers.trees.j48.J48)",
	      "W", 1, "-W"));
	newVector.addElement(new Option(
              "\tSeed for random number generator.\n"
	      +"\tIf set to -1, use a random seed.\n"
              + "\t(default 0)",
              "S", 1, "-S"));
	newVector.addElement(new Option(
				    "\tFactor that determines number of artificial examples to generate.\n"
				    +"\tSpecified proportional to training set size.\n" 
				    + "\t(default 1.0)",
				    "R", 1, "-R"));
    
	newVector.addElement(new Option(
              "\tSample selection scheme.\n"
	      +"\t0=Soft, 1=Hard.\n"
              + "\t(default 0)",
              "A", 1, "-A"));

	if ((m_Classifier != null) &&
	    (m_Classifier instanceof OptionHandler)) {
	    newVector.addElement(new Option(
					    "",
					    "", 0, "\nOptions specific to classifier "
					    + m_Classifier.getClass().getName() + ":"));
	    Enumeration enum = ((OptionHandler)m_Classifier).listOptions();
	    while (enum.hasMoreElements()) {
		newVector.addElement(enum.nextElement());
	    }
	}
	
	return newVector.elements();
    }

    
    /**
     * Parses a given list of options. Valid options are:<p>
     *   
     * -D <br>
     * Turn on debugging output.<p>
     *    
     * -W classname <br>
     * Specify the full class name of a weak classifier as the basis for 
     * Decorate (required).<p>
     *
     * -I num <br>
     * Specify the desired size of the committee (default 15). <p>
     *
     * -M iterations <br>
     * Set the maximum number of Decorate iterations (default 50). <p>
     *
     * -S seed <br>
     * Seed for random number generator. (default 0).<p>
     *
     * -R factor <br>
     * Factor that determines number of artificial examples to generate. <p>
     *
     * Options after -- are passed to the designated classifier.<p>
     *
     * @param options the list of options as an array of strings
     * @exception Exception if an option is not supported
     */
    public void setOptions(String[] options) throws Exception {
	setDebug(Utils.getFlag('D', options));

	String desiredSize = Utils.getOption('I', options);
	if (desiredSize.length() != 0) {
	    setDesiredSize(Integer.parseInt(desiredSize));
	} else {
	    setDesiredSize(15);
	}

	String maxIterations = Utils.getOption('M', options);
	if (maxIterations.length() != 0) {
	    setNumIterations(Integer.parseInt(maxIterations));
	} else {
	    setNumIterations(50);
	}
	
	String seed = Utils.getOption('S', options);
	if (seed.length() != 0) {
	    setSeed(Integer.parseInt(seed));
	} else {
	    setSeed(0);
	}
	
	String artSize = Utils.getOption('R', options);
	if (artSize.length() != 0) {
	    setArtificialSize(Double.parseDouble(artSize));
	} else {
	    setArtificialSize(1.0);
	}
	
	String selectionScheme = Utils.getOption('A', options);
	if (selectionScheme.length() != 0) {
	    setSelectionScheme(Integer.parseInt(selectionScheme));
	} else {
	    setSelectionScheme(0);
	}

	String classifierName = Utils.getOption('W', options);
	if (classifierName.length() == 0) {
	    throw new Exception("A classifier must be specified with"
				+ " the -W option.");
	}
	setClassifier(Classifier.forName(classifierName,
					 Utils.partitionOptions(options)));
    }
    
    /**
     * Gets the current settings of the Classifier.
     *
     * @return an array of strings suitable for passing to setOptions
     */
    public String [] getOptions() {
	String [] classifierOptions = new String [0];
	if ((m_Classifier != null) && 
	    (m_Classifier instanceof OptionHandler)) {
	    classifierOptions = ((OptionHandler)m_Classifier).getOptions();
	}
	String [] options = new String [classifierOptions.length + 14];
	int current = 0;
	if (getDebug()) {
	    options[current++] = "-D";
	}
	options[current++] = "-S"; options[current++] = "" + getSeed();
	options[current++] = "-I"; options[current++] = "" + getDesiredSize();
	options[current++] = "-M"; options[current++] = "" + getNumIterations();
	options[current++] = "-R"; options[current++] = "" + getArtificialSize();
	options[current++] = "-A"; options[current++] = "" + getSelectionScheme();
	
	if (getClassifier() != null) {
	    options[current++] = "-W";
	    options[current++] = getClassifier().getClass().getName();
	}
	options[current++] = "--";
	System.arraycopy(classifierOptions, 0, options, current, 
			 classifierOptions.length);
	current += classifierOptions.length;
	while (current < options.length) {
	    options[current++] = "";
	}
	return options;
    }
    
    
    /**
     * Get the value of m_SelectionScheme.
     * @return value of m_SelectionScheme.
     */
    public int getSelectionScheme() {
	return m_SelectionScheme;
    }
    
    /**
     * Set the value of m_SelectionScheme.
     * @param v  Value to assign to m_SelectionScheme.
     */
    public void setSelectionScheme(int  v) {
	m_SelectionScheme = v;
    }
    
    /**
     * Set debugging mode
     *
     * @param debug true if debug output should be printed
     */
    public void setDebug(boolean debug) {
	m_Debug = debug;
    }
    
    /**
     * Get whether debugging is turned on
     *
     * @return true if debugging output is on
     */
    public boolean getDebug() {
	return m_Debug;
    }
    
    /**
     * Set the base classifier for Decorate.
     *
     * @param newClassifier the Classifier to use.
     */
    public void setClassifier(Classifier newClassifier) {
	m_Classifier = newClassifier;
    }

    /**
     * Get the classifier used as the base classifier
     *
     * @return the classifier used as the classifier
     */
    public Classifier getClassifier() {
	return m_Classifier;
    }

    /**
     * Factor that determines number of artificial examples to generate.
     *
     * @return factor that determines number of artificial examples to generate
     */
    public double getArtificialSize() {
	return m_ArtSize;
    }
  
    /**
     * Sets factor that determines number of artificial examples to generate.
     *
     * @param newwArtSize factor that determines number of artificial examples to generate
     */
    public void setArtificialSize(double newArtSize) {
	m_ArtSize = newArtSize;
    }
    
    /**
     * Gets the desired size of the committee.
     *
     * @return the desired size of the committee
     */
    public int getDesiredSize() {
	return m_DesiredSize;
    }
    
    /**
     * Sets the desired size of the committee.
     *
     * @param newDesiredSize the desired size of the committee
     */
    public void setDesiredSize(int newDesiredSize) {
	m_DesiredSize = newDesiredSize;
    }
    
    /**
     * Sets the max number of Decorate iterations to run.
     *
     * @param numIterations  max number of Decorate iterations to run
     */
    public void setNumIterations(int numIterations) {
	m_NumIterations = numIterations;
    }

    /**
     * Gets the max number of Decorate iterations to run.
     *
     * @return the  max number of Decorate iterations to run
     */
    public int getNumIterations() {
        return m_NumIterations;
    }
    
    /**
     * Set the seed for random number generator.
     *
     * @param seed the random number seed 
     */
    public void setSeed(int seed) {
	m_Seed = seed;
	if(m_Seed==-1){
	    m_Random = new Random();
	}else{
	    m_Random = new Random(m_Seed);
	}
    }
    
    /**
     * Gets the seed for the random number generator.
     *
     * @return the seed for the random number generator
     */
    public int getSeed() {
        return m_Seed;
    }

    /**
     * Build Decorate classifier
     *
     * @param data the training data to be used for generating the classifier
     * @exception Exception if the classifier could not be built successfully
     */
    public void buildClassifier(Instances data) throws Exception {
	if(m_Classifier == null) {
	    throw new Exception("A base classifier has not been specified!");
	}
	if(data.checkForStringAttributes()) {
	    throw new UnsupportedAttributeTypeException("Cannot handle string attributes!");
	}
	if(data.classAttribute().isNumeric()) {
	    throw new UnsupportedClassTypeException("Decorate can't handle a numeric class!");
	}
	if(m_NumIterations < m_DesiredSize)
	    throw new Exception("Max number of iterations must be >= desired ensemble size!");
	
	trainSelectionCommittee(data);


	int i = 1;//current committee size
	int numTrials = 1;//number of Decorate iterations 
	Instances divData = new Instances(data);//local copy of data - diversity data
	Instances artData = null;//artificial data

	//compute number of artficial instances to add at each iteration
	int artSize = (int) (Math.abs(m_ArtSize)*data.numInstances());
	if(artSize==0) artSize=1;//atleast add one random example
	computeStats(data);//Compute training data stats for creating artificial examples
	
	//initialize new committee
	m_Committee = new Vector();
	Classifier newClassifier = m_Classifier;
	newClassifier.buildClassifier(divData);
	m_Committee.add(newClassifier);
	double eComm = computeError(divData);//compute ensemble error
	//if(m_Debug) System.out.println("Initialize:\tClassifier "+i+" added to ensemble. Ensemble error = "+eComm);
	
	//repeat till desired committee size is reached OR the max number of iterations is exceeded 
	while(i<m_DesiredSize && numTrials<m_NumIterations){
	    //Generate artificial training examples
	    artData = generateArtificialData(artSize, data);
	    
	    //Label artificial examples
	    labelData(artData);
	    
	    //Remove all the artificial data from the previous step (if any)
	    if(divData.numInstances() > data.numInstances()) {
		removeInstances(divData, artSize);
	    }
	    addInstances(divData, artData);//Add new artificial data
	    
	    //Build new classifier
	    Classifier tmp[] = Classifier.makeCopies(m_Classifier,1);
	    newClassifier = tmp[0]; 
	    newClassifier.buildClassifier(divData);
	    
	    //Test if the new classifier should be added to the ensemble
	    m_Committee.add(newClassifier);//add new classifier to current committee
	    double currError = computeError(data);
	    if(currError <= eComm){//adding the new member did not increase the error
		i++;
		eComm = currError;
		//if(m_Debug) System.out.println("Iteration: "+(1+numTrials)+"\tClassifier "+i+" added to ensemble. Ensemble error = "+eComm);
	    }else{//reject the current classifier because it increased the ensemble error 
		m_Committee.removeElementAt(m_Committee.size()-1);//pop the last member
	    }
	    numTrials++;
	}
    }

    //Train alternate ensemble method for use in selection
    protected void trainSelectionCommittee(Instances data) throws Exception{
    	if(m_SelectionScheme==BAGGING){
	    if(m_SelectionCommittee==null){//initialize Bagging
		System.out.println("Initializing Bagging...");
		m_SelectionCommittee = new Bagging();
		((Bagging)m_SelectionCommittee).setClassifier(getClassifier());
		((Bagging)m_SelectionCommittee).setSeed(getSeed());
		((Bagging)m_SelectionCommittee).setNumIterations(getDesiredSize());
		((Bagging)m_SelectionCommittee).setBagSizePercent(100);
	    }
	    m_SelectionCommittee.buildClassifier(data);
	}else if(m_SelectionScheme==BOOSTING){
	    if(m_SelectionCommittee==null){//initialize Boosting
		System.out.println("Initializing AdaBoost...");
		m_SelectionCommittee = new AdaBoostM1();
		((AdaBoostM1)m_SelectionCommittee).setClassifier(getClassifier());
		((AdaBoostM1)m_SelectionCommittee).setSeed(getSeed());
		((AdaBoostM1)m_SelectionCommittee).setMaxIterations(getDesiredSize());
	    }
	    m_SelectionCommittee.buildClassifier(data);
	}
    }

    
    /** 
     * Compute and store statistics required for generating artificial data.
     *
     * @param data training instances
     * @exception Exception if statistics could not be calculated successfully
     */
    protected void computeStats(Instances data) throws Exception{
	int numAttributes = data.numAttributes();
	m_AttributeStats = new Vector(numAttributes);//use to map attributes to their stats
	
	for(int j=0; j<numAttributes; j++){
	    if(data.attribute(j).isNominal()){
		//Compute the probability of occurence of each distinct value 
		int []nomCounts = (data.attributeStats(j)).nominalCounts;
		double []counts = new double[nomCounts.length];
		if(counts.length < 2) throw new Exception("Nominal attribute has less than two distinct values!"); 
		//Perform Laplace smoothing
		for(int i=0; i<counts.length; i++)
		    counts[i] = nomCounts[i] + 1;
		Utils.normalize(counts);
		double []stats = new double[counts.length - 1];
		stats[0] = counts[0];
		//Calculate cumulative probabilities
		for(int i=1; i<stats.length; i++)
		    stats[i] = stats[i-1] + counts[i];
		m_AttributeStats.add(j,stats);
	    }else if(data.attribute(j).isNumeric()){
		//Get mean and standard deviation from the training data
		double []stats = new double[2];
		stats[0] = data.meanOrMode(j);
		stats[1] = Math.sqrt(data.variance(j));
		m_AttributeStats.add(j,stats);
	    }else System.err.println("Decorate can only handle numeric and nominal values.");
	}
    }

    /**
     * Generate artificial training examples.
     * @param artSize size of examples set to create
     * @param data training data
     * @return the set of unlabeled artificial examples
     */
    protected Instances generateArtificialData(int artSize, Instances data){
	int numAttributes = data.numAttributes();
	Instances artData = new Instances(data, artSize);
	double []att; 
	Instance artInstance;
	
	for(int i=0; i<artSize; i++){
	    att = new double[numAttributes];
	    for(int j=0; j<numAttributes; j++){
		if(data.attribute(j).isNominal()){
		    //Select nominal value based on the frequency of occurence in the training data  
		    double []stats = (double [])m_AttributeStats.get(j);
		    att[j] =  (double) selectIndexProbabilistically(stats);
		}
		else if(data.attribute(j).isNumeric()){
		    //Generate numeric value from the Guassian distribution 
		    //defined by the mean and std dev of the attribute
		    double []stats = (double [])m_AttributeStats.get(j);
		    att[j] = (m_Random.nextGaussian()*stats[1])+stats[0];
		}else System.err.println("Decorate can only handle numeric and nominal values.");
	    }
	    artInstance = new Instance(1.0, att);
	    artData.add(artInstance);
	}
	return artData;
    }
    
    
    /** 
     * Labels the artificially generated data.
     *
     * @param artData the artificially generated instances
     * @exception Exception if instances cannot be labeled successfully 
     */
    protected void labelData(Instances artData) throws Exception {
	Instance curr;
	double []probs;
	
	for(int i=0; i<artData.numInstances(); i++){
	    curr = artData.instance(i);
	    //compute the class membership probs predicted by the current ensemble 
	    probs = distributionForInstance(curr);
	    //select class label inversely proportional to the ensemble predictions
	    curr.setClassValue(inverseLabel(probs));
	}	
    }
    

    /** 
     * Select class label such that the probability of selection is
     * inversely proportional to the ensemble's predictions.
     *
     * @param probs class membership probabilities of instance
     * @return index of class label selected
     * @exception Exception if instances cannot be labeled successfully 
     */
    protected int inverseLabel(double []probs) throws Exception{
	double []invProbs = new double[probs.length];
	//Produce probability distribution inversely proportional to the given
	for(int i=0; i<probs.length; i++){
	    if(probs[i]==0){
		invProbs[i] = Double.MAX_VALUE/probs.length; 
		//Account for probability values of 0 - to avoid divide-by-zero errors
		//Divide by probs.length to make sure normalizing works properly
	    }else{
		invProbs[i] = 1.0 / probs[i];
	    }
	}
	Utils.normalize(invProbs);
	double []cdf = new double[invProbs.length];
	//Compute cumulative probabilities 
	cdf[0] = invProbs[0];
	for(int i=1; i<invProbs.length; i++){
	    cdf[i] = invProbs[i]+cdf[i-1];
	}
	
	if(Double.isNaN(cdf[invProbs.length-1]))
	    System.err.println("Cumulative class membership probability is NaN!"); 
	return selectIndexProbabilistically(cdf);
    }
    
    /** 
     * Given cumulative probabilities select a nominal attribute value index 
     *
     * @param cdf array of cumulative probabilities
     * @return index of attribute selected based on the probability distribution 
     */
    protected int selectIndexProbabilistically(double []cdf){
	double rnd = m_Random.nextDouble();
	int index = 0;
	while(index < cdf.length && rnd > cdf[index]){
	    index++;
	}
	return index;
    } 
    
    /**
     * Removes a specified number of instances from the given set of instances.
     *
     * @param data given instances
     * @param numRemove number of instances to delete from the given instances
     */
    protected void removeInstances(Instances data, int numRemove){
	int num = data.numInstances();
	for(int i=num - 1; i>num - 1 - numRemove;i--){
	    data.delete(i);
	}
    }
    
    /**
     * Add new instances to the given set of instances.
     *
     * @param data given instances
     * @param newData set of instances to add to given instances
     */
    protected void addInstances(Instances data, Instances newData){
	for(int i=0; i<newData.numInstances(); i++)
	    data.add(newData.instance(i));
    }
    
    /** 
     * Computes the error in classification on the given data.
     *
     * @param data the instances to be classified
     * @return classification error
     * @exception Exception if error can not be computed successfully
     */
    protected double computeError(Instances data) throws Exception {
	double error = 0.0;
	int numInstances = data.numInstances();
	Instance curr;
	
	for(int i=0; i<numInstances; i++){
	    curr = data.instance(i);
	    //Check if the instance has been misclassified
	    if(curr.classValue() != ((int) classifyInstance(curr))) error++;
	}
	return (error/numInstances);
    }
    
  /**
   * Calculates the class membership probabilities for the given test instance.
   *
   * @param instance the instance to be classified
   * @return predicted class probability distribution
   * @exception Exception if distribution can't be computed successfully
   */
  public double[] distributionForInstance(Instance instance) throws Exception {
      if (instance.classAttribute().isNumeric()) {
	  throw new UnsupportedClassTypeException("Decorate can't handle a numeric class!");
      }
      double [] sums = new double [instance.numClasses()], newProbs; 
      Classifier curr;
      
      for (int i = 0; i < m_Committee.size(); i++) {
	  curr = (Classifier) m_Committee.get(i);
	  if (curr instanceof DistributionClassifier) {
	      newProbs = ((DistributionClassifier)curr).distributionForInstance(instance);
	      for (int j = 0; j < newProbs.length; j++)
		  sums[j] += newProbs[j];
	  } else {
	      sums[(int)curr.classifyInstance(instance)]++;
	  }
      }
      if (Utils.eq(Utils.sum(sums), 0)) {
	  return sums;
      } else {
	  Utils.normalize(sums);
	  return sums;
      }
  }  

    
    /** 
     * Given a set of unlabeled examples, select a specified number of examples to be labeled.
     * @param activePool pool of unlabeled examples
     * @param num number of examples to selcted for labeling
     * @exception Exception if selective sampling fails
     */
    public int [] selectInstancesForFeatures(Instances activePool,int num) throws Exception{
	//Make a list of pairs of indices and the corresponding measure of informativenes of examples
	//Sort this in the order of informativeness and return the list of num indices
	int poolSize = activePool.numInstances();
	Pair []pairs = new Pair[poolSize];
	for(int i=0; i<poolSize; i++){
	    pairs[i] = new Pair(i,calculateScore(activePool.instance(i)));
	}
	
	//sort in AScending order
	Arrays.sort(pairs, new Comparator() {
                public int compare(Object o1, Object o2) {
		    double diff = ((Pair)o2).second - ((Pair)o1).second; 
		    return(diff < 0 ? 1 : diff > 0 ? -1 : 0);
		}
            });
	int []selected = new int[num];
	if(m_Debug) System.out.println("Sorted list:");
	for(int j=0; j<num; j++){
	    if(m_Debug) System.out.println("\t"+pairs[j].second+"\t"+pairs[j].first);
	    selected[j] = pairs[j].first;
	}
	return selected;
    }


    /**
     * Calculate the feature acquisition score for 
     * given examples depending on the chosen selection scheme.
     * @param instance local instances from the current pool
     * @return score
     * @exception Exception if score could not be calculated properly */
    protected double calculateScore(Instance instance) throws Exception{
	double score;
	
	switch(m_SelectionScheme){
	case ENTROPY:
	    score = -1.0*calculateEntropy(instance);
	    break;    
	case UNLABELED_MARGIN:
	    score = calculateMargin(instance);
	    break;
	case ABS_LABEL_MARGIN:
	    score = Math.abs(calculateLabeledInstanceMargin(instance));
	    break;
	case LABEL_MARGIN:
	    score = calculateLabeledInstanceMargin(instance);
	    break;
	case RANDOM:
	    score = 0;//all instances are equal
	    break;
	default:
	    score = 0;//all instances are equal
	    break; 
	}
	return score;
    }    
    

    /** 
     * Given a set of unlabeled examples, select a specified number of examples to be labeled.
     * @param unlabeledActivePool pool of unlabeled examples
     * @param num number of examples to selcted for labeling
     * @exception Exception if selective sampling fails
     */
    public int [] selectInstances(Instances unlabeledActivePool,int num) throws Exception{
	//Make a list of pairs of indices and the corresponding measure of informativenes of examples
	//Sort this in the order of informativeness and return the list of num indices
	int poolSize = unlabeledActivePool.numInstances();
	Pair []pairs = new Pair[poolSize];
	for(int i=0; i<poolSize; i++){
	    pairs[i] = new Pair(i,calculateDisagreement(unlabeledActivePool.instance(i)));
	}
	//sort in descending order
	Arrays.sort(pairs, new Comparator() {
                public int compare(Object o1, Object o2) {
		    double diff = ((Pair)o1).second - ((Pair)o2).second; 
		    return(diff < 0 ? 1 : diff > 0 ? -1 : 0);
		}
            });
	int []selected = new int[num];
	if(m_Debug) System.out.println("Sorted list:");
	for(int j=0; j<num; j++){
	    if(m_Debug) System.out.println("\t"+pairs[j].second+"\t"+pairs[j].first);
	    selected[j] = pairs[j].first;
	}
	return selected;
    }

    
    /**
     * Calculate the disagreement in the ensemble over the label of
     * given examples depending on the chosen selection scheme.
     * @param instance unlabeled instance from the current pool
     * @return nomalized measure of disagreement
     * @exception Exception if disagreement could not be calculated properly */
    protected double calculateDisagreement(Instance instance) throws Exception{
	double disagreement;
	switch(m_SelectionScheme){
	case JENSEN_SHANNON:
	    disagreement = calcJSDivergence(instance);
	    break;
	case MAJORITY:
	    disagreement = calcMajorityDis(instance);
	    break;
	case EUCLIDEAN:
	    disagreement = calcEuclideanDis(instance);
	    break;
	case MARGIN:
	    //negate margins so that the sort ordering does not need to be changed
	    disagreement = -1.0 * calculateMargin(instance);
	    break;
	case BAGGING:
	    disagreement = -1.0 * m_SelectionCommittee.calculateMargin(instance);
	    break;
	case BOOSTING:
	    disagreement = -1.0 * m_SelectionCommittee.calculateMargin(instance);
	    break;
	default:
	    disagreement = calcMajorityDis(instance);
	}
	return disagreement;
    }    
    
    /**
     * Calculate the disagreement in the ensemble over the label of
     * given examples.  The disagreement is calculated between the
     * posterior probabilities of each member classifier and those of
     * the ensemble.
     * @param instance unlabeled instance from the current pool
     * @return nomalized measure of disagreement
     * @exception Exception if disagreement could not be calculated properly
     */
    protected double calcJSDivergence(Instance instance) throws Exception{
	if (!(m_Classifier instanceof DistributionClassifier)) 
	    System.err.println("JS Divergence can only be applied to DistributionClassifiers.");
	//if(m_Debug) System.out.println("Using JS Divergence.");
	int size = m_Committee.size();
	double [][]probs = new double [size][];
	double [] avg = new double [instance.numClasses()];
	Classifier curr;
	for (int i = 0; i < m_Committee.size(); i++) {
	    curr = (Classifier) m_Committee.get(i);
	    probs[i] = ((DistributionClassifier)curr).distributionForInstance(instance);
	    smoothDistribution(probs[i]);
	    for (int j = 0; j < avg.length; j++)
		avg[j] += probs[i][j];
	}
	Utils.normalize(avg);
	
	double disagreement = 0.0;
	for(int i=0; i<size; i++){
	    disagreement += calcKLdivergence(probs[i], avg);
	}
	disagreement = disagreement/m_Committee.size();
	return disagreement;
    }
          
    //Smooth given probability distribution to get rid of zero values
    protected void smoothDistribution(double []probs){
	for(int i=0; i<probs.length; i++)
	    if(probs[i]==0) probs[i] = m_Epsilon;
	Utils.normalize(probs);
    }
    
    /**
     * Calculate the KL divergence between two probability distributions.
     * @param p1 first probability disttribution
     * @param p1 second probability disttribution
     * @return the KL divergence between p1 and p2 */
    protected double calcKLdivergence(double []p1, double []p2){
	double kl = 0.0;
	for(int i=0; i<p1.length; i++){
	    kl += p1[i]*Math.log(p2[i]/p1[i]);
	}
	kl = -1.0 * kl;
	return kl;
    }
    
    /**
     * Calculate the disagreement in the ensemble over the label of
     * given examples.  The disagreement is calculated using the
     * Jensen-Shannon divergence of the posterior probabilities
     * @param instance unlabeled instance from the current pool
     * @return nomalized measure of disagreement 
     * @exception Exception if disagreement could not be calculated properly
     */
    protected double calcEuclideanDis(Instance instance) throws Exception{
	if (!(m_Classifier instanceof DistributionClassifier)) 
	    System.err.println("Euclidean disagreement can only be applied to DistributionClassifiers.");
	//if(m_Debug) System.out.println("Using Euclidean disagreement.");
	double disagreement = 0.0;
	double []pred = distributionForInstance(instance);//ensemble decision
	Classifier curr;
	for (int i = 0; i < m_Committee.size(); i++) {
	    curr = (Classifier) m_Committee.get(i);
	    double sum = 0.0; 
	    double []newProbs = ((DistributionClassifier)curr).distributionForInstance(instance);
	    for (int j = 0; j < newProbs.length; j++)
		sum += Math.pow((newProbs[j]-pred[j]),2);
	    disagreement += Math.sqrt(sum);
	}
	disagreement = disagreement/m_Committee.size();
	//This normalization step is not necessary for selection, but
	//is useful for comparing disagreement values for different
	//points on the learning curves (as committee size can change)
	return disagreement;
    }
    

    /**
     * Calculate the disagreement in the ensemble over the label of given examples.
     * @param instance unlabeled instance from the current pool
     * @return nomalized measure of disagreement
     * @exception Exception if disagreement could not be calculated properly
     */
    protected double calcMajorityDis(Instance instance) throws Exception{
	//if(m_Debug) System.out.println("Using majority vote disagreement.");
	double disagreement = 0.0;
	double pred = classifyInstance(instance);//ensemble decision
	Classifier curr;
	for (int i = 0; i < m_Committee.size(); i++) {
	  curr = (Classifier) m_Committee.get(i);
	  if(curr.classifyInstance(instance) != pred) disagreement++;
	}
	disagreement = disagreement/m_Committee.size();
	//This normalization step is not necessary for selection, but
	//is useful for comparing disagreement values for different
	//points on the learning curves (as committee size can change)
	return disagreement;
    }

     
    /**
     * Returns description of the Decorate classifier.
     *
     * @return description of the Decorate classifier as a string
     */
    public String toString() {
	if (m_Committee == null) {
	    return "Decorate: No model built yet.";
	}
	StringBuffer text = new StringBuffer();
	text.append("Decorate base classifiers: \n\n");
	for (int i = 0; i < m_Committee.size(); i++)
	    text.append(((Classifier) m_Committee.get(i)).toString() + "\n\n");
	text.append("Number of classifier in the ensemble: "+m_Committee.size()+"\n");
	return text.toString();
    }
    
    
    /**
     * Main method for testing this class.
     *
     * @param argv the options
     */
    public static void main(String [] argv) {
	try {
	    System.out.println(Evaluation.evaluateModel(new Fable(), argv));
	} catch (Exception e) {
	    System.err.println(e.getMessage());
	}
    }
}
