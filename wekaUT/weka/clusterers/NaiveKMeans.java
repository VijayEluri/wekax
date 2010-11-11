package weka.clusterers;

import java.util.*;
import weka.core.*;
import weka.core.metrics.*;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.ReplaceMissingValues;
import weka.clusterers.initializers.*;

public class NaiveKMeans extends Clusterer implements OptionHandler{
	public boolean metricBuilt=true;
	private ReplaceMissingValues replaceMissingValues;
    private Initializer initializer=new RandomInitializer();
	public boolean doEvaluate=true;
	
	public void buildClusterer(Instances data) throws Exception{
		replaceMissingValues=new ReplaceMissingValues();
		replaceMissingValues.setInputFormat(data);
		instances=Filter.useFilter(data,replaceMissingValues);
		if(centroids.numInstances()==0){
        initializer.setClusterer(this);
        centroids=initializer.initialize();
		}
        instanceses=new Instances[K];
        clusters=new ArrayList[K];
		assignments=new int[instances.numInstances()];
        int loop=0;
		while(true){
            System.out.println("===***=== "+loop+" ===***===");
			if(clusterInstances())break;
			centroids=new Instances(instances,0);
			for(int i=0;i<K;i++){
				centroids.add(instanceses[i].mean());
				System.out.print(instanceses[i].numInstances()+"\t");
			}
			System.out.println();
            loop++;
		}
		if(doEvaluate)evaluate();
	}
    
    public void evaluate() throws Exception{
        for(int i=0,I;i<K;i++){
            System.out.println("Cluster "+i+":");
            I=instanceses[i].numInstances();
            double [] distances=new double[I];
            double [] dis=new double[I];
            int [] indexes=new int[I];
            double distance;
            for(int j=0;j<I;j++){
                distance=metric.distance(centroids.instance(i),instanceses[i].instance(j));
                distances[j]=distance;
                dis[j]=distance;
                indexes[j]=((Integer)(clusters[i].get(j))).intValue();
            }
            Arrays.sort(dis);
            for(int j=0;j<20&&j<I;j++){
                for(int k=0;k<I;k++){
                    if(distances[k]==dis[j]){
                        System.out.println("\tcenter: "+indexes[k]);
                        break;
                    }
                }
            }
            for(int j=I-1;j>I-21&&j>-1;j--){
                for(int k=0,kk=I;k<kk;k++){
                    if(distances[k]==dis[j]){
                        System.out.println("\tcircle: "+indexes[k]);
                        break;
                    }
                }
            }
        }
    }
	
	public Enumeration listOptions(){
		Vector vector=new Vector(3);
		vector.addElement(new Option("\tnumber of clusters.","N",1,"-N <num>"));
        vector.addElement(new Option("\tmetric.\tdefault=weka.core.metrics.Euclidean","M",1,"-M <metric class>"));
        vector.addElement(new Option("\tinitializer.\tdefault=weka.clusters.initializers.RandomInitializer","I",1,"-I <initializer class>"));
		return vector.elements();
	}
	
	public void setOptions(String [] options) throws Exception{
		String string;
		string=Utils.getOption('N',options);
		if(string.length()!=0){
			K=Integer.parseInt(string);
		}
        string=Utils.getOption('M',options);
        if(string.length()!=0){
            metric=(Metric)Utils.forName(Metric.class,string,options);
			metricBuilt=false;
        }
        string=Utils.getOption('I',options);
        if(string.length()!=0){
            initializer=(Initializer)Utils.forName(Initializer.class,string,options);
        }
	}
	
	public String [] getOptions(){
		String [] options=new String[6];
		int current=0;
		options[current++]="-N";
		options[current++]=Integer.toString(K);
        options[current++]="-M";
        options[current++]=metric.getClass().getName();
        options[current++]="-I";
        options[current++]=initializer.getClass().getName();
		return options;
	}
	
	public static void main(String [] argv){
		try{
			System.out.println(ClusterEvaluation.evaluateClusterer(new NaiveKMeans(),argv));
		}catch(Exception e){
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}
}
