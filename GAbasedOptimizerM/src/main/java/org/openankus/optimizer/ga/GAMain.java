package org.openankus.optimizer.ga;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Date;

import org.openankus.optimizer.ml.Algorithm;
import org.openankus.optimizer.ml.DecisionTreeC45;
import org.openankus.optimizer.ml.KNN;
import org.openankus.optimizer.ml.MLP;
import org.openankus.optimizer.ml.Model;
import org.openankus.optimizer.ml.Parameter;
import org.openankus.optimizer.ml.RForest;

import weka.core.Instances;

public class GAMain {
	
	private static CGA _ga	= null;
	private static Parameter[] _parameters = null;

	public static void main(String[] args) throws Exception {
		
		//GA ȯ�溯�� ����
		//cmd ��: -s 1 -p 10 -mG 3 -cp 0.9 -mp 0.5 -bs 5 -in D:/Programs/data/iris.arff
		int 	seed = 10;
		int 	popSize = 300;
		int		maxGeneration = 500;
		float 	crossProb = 0.9f;
		float	mutProb = 0.5f;
		int		binaryStrSize = 5;	// �������ڿ� ũ��
		String  inputFile = "D:/Programs/data/nursery_shuffle.arff";
		
		String parname = "";
		for(String arg:args){
			if(parname.isEmpty() && arg.startsWith("-")){
				parname =arg;
			}else{
				if(parname.equals("-s")){
					seed = Integer.parseInt(arg);			// ��������
					System.out.println("��������: "+seed);
				}else if(parname.equals("-p")){
					popSize = Integer.parseInt(arg); 		// ��üũ��
					System.out.println("��üũ��: "+popSize);
				}else if(parname.equals("-mG")){
					maxGeneration = Integer.parseInt(arg); 	// �ִ� �����
					System.out.println("�ִ� �����: "+maxGeneration);
				}else if(parname.equals("-cp")){
					crossProb = Float.parseFloat(arg);		// ����Ȯ��
					System.out.println("����Ȯ��: "+crossProb);
				}else if(parname.equals("-mp")){
					mutProb = Float.parseFloat(arg);		// �������� Ȯ��
					System.out.println("�������� Ȯ��: "+mutProb);
				}else if(parname.equals("-bs")){
					binaryStrSize = Integer.parseInt(arg);	// �������ڿ� ũ��
					System.out.println("�������ڿ� ũ��: "+binaryStrSize);
				}else if(parname.equals("-in")){
					inputFile = arg;						// �Էµ����� ����
					System.out.println("�Էµ�����: "+inputFile);
				}
				parname = "";
			}
		}
		
		
		// GA ��ü ����
		_ga = new CGA();
		_ga.setParameters(popSize,seed,crossProb,mutProb);
		
		// �ʱ� ��ü���� ����
		int 	numAttri;	
		int		classIndex		= 8;		// �Էµ����Ϳ� ���� Ŭ���� �Ӽ� ����
		
		// weka �Էµ����� �ҷ��� (arff ����)
		BufferedReader reader = new BufferedReader(new FileReader(inputFile));
		Instances data = new Instances(reader);
		numAttri = data.numAttributes();
		System.out.println("�Ӽ� ����: "+numAttri);
		System.out.println("������ ����: "+data.numInstances());

		data.setClassIndex(classIndex);
		
		Model model = new Model(data);
		Algorithm 	algorithm = null;
		int			numAlgPara = -1;
	
		switch("RandomForest"){
		case "C45":
			System.out.println("C45");
			algorithm = new DecisionTreeC45();
			numAlgPara = 2;
			_parameters = new Parameter[numAlgPara];
			_parameters[0] = new Parameter("CF",0.1f,0.5f);
			_parameters[1] = new Parameter("min",2.0f,80.0f);
			break;
		case "MLP":
			System.out.println("MLP");			
			algorithm = new MLP();
			numAlgPara = 3;
			_parameters = new Parameter[numAlgPara];
			_parameters[0] = new Parameter("lr",0.1f,1.0f);
			_parameters[1] = new Parameter("mm",0.1f,1.0f);
			_parameters[2] = new Parameter("h",1.0f,50.0f);
			break;
		case "KNN":
			System.out.println("KNN");	
			algorithm = new KNN();
			numAlgPara = 1;
			_parameters = new Parameter[numAlgPara];
			_parameters[0] = new Parameter("k",1.0f,50.f);
			break;
		case "RandomForest":
			System.out.println("RandomForest");	
			algorithm = new RForest();
			numAlgPara = 2;
			_parameters = new Parameter[numAlgPara];
			_parameters[0] = new Parameter("MD",0.0f,50.0f);
			_parameters[1] = new Parameter("numDT",1.0f,100.0f);
			
		};
		
		if(numAlgPara != -1){
			
			Date startTime = new Date();
			
			// ��ü�ʱ�ȭ
			_ga.setInitialPopulation(numAttri,numAlgPara,binaryStrSize,classIndex);
			//System.out.println("��ü �ʱ�ȭ �Ϸ�...");
			
			// ��ü��	
			_ga.evaluation(model,algorithm,_parameters);
			//System.out.println(_ga.toStringChroms());
			//System.out.println("----- elitist()");
			//System.out.println(_ga.getelitist().toStringGene()+" - "+_ga.getelitist().getFitness());
			//System.exit(1);

			int generation = 0;
			System.out.println(" ************************************ "+generation+" ����: "+_ga.getelitist().getFitness());
			//System.out.println(_ga.getelitist().toStringGene());
			//System.exit(1);
			
			do{
				// ��ü����
				_ga.selectMethod();
				//System.out.println("��ü ���� �Ϸ�..");
			
				// ���� Ȯ��
				_ga.crossover();
				//System.out.println("��ü ���� �Ϸ�..");
			
				// �������� Ȯ��
				_ga.mutation(classIndex);
				//System.out.println("��ü �������� �Ϸ�..");
				
				// ��ü��
				//System.out.println(_ga.toStringChroms());
				_ga.evaluation(model,algorithm,_parameters);
				//System.out.println("��ü�� �Ϸ�..");

				generation++;
				
				System.out.println(" ************************************ "+generation+" ����: "+_ga.getelitist().getFitness());

				if(generation == maxGeneration){
					System.out.println(_ga.getelitist().toStringGene());
					_ga.getelitist().toStringModel();
				}
	
			}while(generation <= maxGeneration);
			
			Date endTime = new Date();
			
			long lTime = (long) ((endTime.getTime() - startTime.getTime())/(60.0*1000.0));
			System.out.println("Start Time: "+ startTime.toString());
			System.out.println("End Time: "+endTime.toString());
			System.out.println("TIME : " + lTime + "��");
			
		}else{
			System.out.println("���� 00001");
		}
		
	}

}
